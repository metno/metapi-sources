/*
    MET-API

    Copyright (C) 2014 met.no
    Contact information:
    Norwegian Meteorological Institute
    Box 43 Blindern
    0313 OSLO
    NORWAY
    E-mail: met-api@met.no

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
    MA 02110-1301, USA
*/

package services.sources

import play.api.Play.current
import play.api.db._
import play.api.libs.ws._
import play.Logger
import anorm._
import anorm.SqlParser._
import java.sql.Connection
import javax.inject.Singleton
import scala.annotation.tailrec
import scala.concurrent._
import scala.language.postfixOps
import scala.util._
import no.met.data._
import no.met.data.AnormUtil._
import no.met.geometry._
import models._
import org.joda.time.format.{ DateTimeFormatter, DateTimeFormat, ISODateTimeFormat }
import org.joda.time.DateTime


//$COVERAGE-OFF$ Not testing database queries

/** Concrete implementation of SourceAccess class, connecting to the MET API's STInfoSys clone database.
 */
@Singleton
class ProdSourceAccess extends SourceAccess {

  private object STInfoSysExec {

    val parser: RowParser[Source] = {
      get[Option[String]]("id") ~
        get[Option[String]]("name") ~
        get[Option[String]]("country") ~
        get[Option[String]]("countryCode") ~
        get[Option[Int]]("wmoidentifier") ~
        get[Option[Double]]("level") ~
        get[Option[Double]]("lat") ~
        get[Option[Double]]("lon") ~
        get[Option[String]]("validfrom") ~
        get[Option[String]]("validto") ~
        get[Option[Int]]("municipalityid") ~
        get[Option[String]]("municipalityname") ~
        get[Option[Int]]("countyid") ~
        get[Option[String]]("countyname") map {
        case sourceid~name~country~countryCode~wmono~hs~lat~lon~fromDate~toDate~municipalityid~municipalityname~countyid~countyname => {
          val (munid, munname, cntid, cntname) = municipalityid match {
            case Some(x) if x == 0 => (None, None, None, None)
            case _ => (municipalityid, municipalityname, countyid, countyname)
          }
          Source(
            "SensorSystem",
            sourceid,
            name,
            country,
            countryCode,
            wmono,
            if (lon.isEmpty || lat.isEmpty) None else Some(Point(coordinates = Seq(lon.get, lat.get))),
            if (hs.isEmpty) None else Some(Seq(Level(Some("height_above_ground"), hs, Some("m"), None))),
            fromDate,
            toDate,
            munid,
            munname,
            cntid,
            cntname
          )
        }
      }
    }

    private def getSelectQuery(fields: Set[String]): String = {
      val legalFields = Set(
        "id", "name", "country", "countrycode", "wmoidentifier", "geometry", "level", "validfrom", "validto",
        "municipalityid", "municipalityname", "countyid", "countyname")
      val illegalFields = fields -- legalFields
      if (illegalFields.nonEmpty) {
        throw new BadRequestException(
          "Invalid fields in the query parameter: " + illegalFields.mkString(","),
          Some(s"Supported fields: ${legalFields.mkString(", ")}"))
      }
      val fieldStr = fields.mkString(", ")
        .replace("geometry", "lat, lon")
      val missing = legalFields -- fields
      if (missing.isEmpty) {
        fieldStr
      }
      else {
        val missingStr = missing.map(x => "NULL AS " + x).mkString(", ").replace("NULL AS geometry", "NULL AS lat, NULL AS LON")
        fieldStr + "," + missingStr
      }
    }

    private val dateFormat = "YYYY-MM-DD"
    private val dtFormatter: DateTimeFormatter = DateTimeFormat.forPattern(dateFormat)

    private def dateExpression(dt: DateTime): String = s"TO_DATE('${dtFormatter.print(dt)}', '$dateFormat')"

    private def intervalInclusionQuery(d0: String, d1: String): String = s"s.fromtime <= $d1 AND (s.totime IS NULL OR $d0 <= s.totime)"

    private def getValidTimeQuery(validTime: Option[String]): String = {
      val currDateExpr = "CURRENT_DATE"
      val vtspec = ValidTimeSpecification(validTime.getOrElse("now"))
      (vtspec.fromDateTime, vtspec.toDateTime) match {
        case (None, None) => intervalInclusionQuery(currDateExpr, currDateExpr)
        case (None, Some(dt)) => { val d = dateExpression(dt); intervalInclusionQuery(d, d) }
        case (Some(dt0), None) => intervalInclusionQuery(dateExpression(dt0), currDateExpr)
        case (Some(dt0), Some(dt1)) => intervalInclusionQuery(dateExpression(dt0), dateExpression(dt1))
      }
    }

    // Converts a string to use '%' for wildcards instead of '*'.
    private def replaceWildcards(s: String): String = {
      s.replaceAll("\\*", "%")
    }

    // scalastyle:off method.length
    def apply(
      ids: Seq[String], geometry: Option[String], validTime: Option[String], name: Option[String],
      country: Option[String], fields: Set[String]): List[Source] = {

      val innerSelectQ = """
        'SN'|| s.stationid AS id,
         s.name AS name,
         c.name AS country,
         c.alias AS countryCode,
         wmono AS wmoidentifier,
         hs AS level,
         lat,
         lon,
         TO_CHAR(s.fromtime, 'YYYY-MM-DD') AS validfrom,
         TO_CHAR(s.totime, 'YYYY-MM-DD') AS validto,
         m.municipid AS municipalityid,
         (CASE WHEN m.municipid = 0 THEN NULL ELSE m.name END) AS municipalityname,
         (CASE WHEN 0 < m.municipid AND m.municipid < 10000 THEN m.municipid / 100 ELSE NULL END) AS countyid,
         (CASE WHEN 0 < m.municipid AND m.municipid < 10000 THEN (SELECT name FROM municip WHERE municipid = m.municipid / 100) ELSE NULL END) AS countyname
      """
      val selectQ = if (fields.isEmpty) "*" else getSelectQuery(fields)

      // Filter by source id
      val idsQ = if (ids.nonEmpty) {
        val idStr = SourceSpecification.stationWhereClause(ids, "s.stationid", None)
        s"($idStr)"
      } else {
        "TRUE"
      }

      val validTimeQ = getValidTimeQuery(validTime)
      val nameQ = if (name.isEmpty) "TRUE" else "lower(s.name) LIKE lower({name})"
      val countryQ = if (country.isEmpty) "TRUE" else "(lower(c.name) LIKE lower({country}) OR lower(c.alias) LIKE lower({country}))"
      val permitQ = "mp.permitid NOT IN (3, 4, 6)"

      val query = if (geometry.isEmpty) {
        s"""
        |SELECT
          |$selectQ
        |FROM
          |(SELECT DISTINCT $innerSelectQ
          |FROM
            |station s, country c, municip m, message_policy mp
          |WHERE
            |$idsQ
            |AND c.countryid = s.countryid
            |AND m.municipid = (CASE WHEN s.municipid IS NULL THEN 0 ELSE s.municipid END)
            |AND mp.stationid = s.stationid
            |AND $validTimeQ
            |AND $nameQ
            |AND $countryQ
            |AND $permitQ
          |ORDER BY
            |id) t0""".stripMargin
      } else {
        val geom = Geometry.decode(geometry.get)
        if (geom.isInterpolated) {
          s"""
          |SELECT
            |$selectQ
          |FROM
            |(SELECT DISTINCT $innerSelectQ
            |FROM
              |station s, country c, municip m, message_policy mp
            |WHERE
              |$idsQ
              |AND c.countryid = s.countryid
              |AND m.municipid = (CASE WHEN s.municipid IS NULL THEN 0 ELSE s.municipid END)
              |AND mp.stationid = s.stationid
              |AND $validTimeQ
              |AND $nameQ
              |AND $countryQ
              |AND $permitQ
            |ORDER BY
              |ST_SetSRID(ST_MakePoint(lon, lat),4326) <-> ST_GeomFromText('${geom.asWkt}',4326), id
            |LIMIT 1) t0""".stripMargin
        } else {
          s"""
          |SELECT
            |$selectQ
          |FROM
            |(SELECT DISTINCT $innerSelectQ
            |FROM
              |station s, country c, municip m, message_policy mp
            |WHERE
              |$idsQ
              |AND c.countryid = s.countryid
              |AND m.municipid = (CASE WHEN s.municipid IS NULL THEN 0 ELSE s.municipid END)
              |AND mp.stationid = s.stationid
              |AND $validTimeQ
              |AND $nameQ
              |AND $countryQ
              |AND $permitQ
              |AND ST_WITHIN(ST_SetSRID(ST_MakePoint(lon, lat),4326), ST_GeomFromText('${geom.asWkt}',4326))
            |ORDER BY
              |id) t0""".stripMargin
        }
      }

      //Logger.debug(query)

      DB.withConnection("sources") { implicit connection =>
        val nameList = if (name.isEmpty) List[String]() else List[String](replaceWildcards(name.get))
        val countryList = if (country.isEmpty) List[String]() else List[String](replaceWildcards(country.get))
        SQL(insertPlaceholders(query, List(("name", nameList.size), ("country", countryList.size))))
          .on(onArg(List(("name", nameList), ("country", countryList))): _*)
          .as( parser * )
      }
    }
    // scalastyle:on method.length
  }


  private object GridIDFExec {

    private val dtFormatter: DateTimeFormatter = ISODateTimeFormat.dateTimeNoMillis
    private val validFrom: DateTime = dtFormatter.parseDateTime(IDFGridConfig.validFrom)
    private val validTo: DateTime = dtFormatter.parseDateTime(IDFGridConfig.validTo)

    def apply(idfGridIds: Seq[String], validTime: Option[String], fields: Set[String]): List[Source] = {

      assert(idfGridIds.isEmpty || ((idfGridIds.length == 1) && (idfGridIds.head == IDFGridConfig.name))) // for now

      val include = {
        val vtspec = ValidTimeSpecification(validTime.getOrElse("now"))

        val currDate: DateTime = null
        (vtspec.fromDateTime, vtspec.toDateTime) match {
          case (None, None) => !validFrom.isAfterNow && !validTo.isBeforeNow // validFrom_<=_CURRDATE_<=_validTo
          case (None, Some(dt)) => !validFrom.isAfter(dt) && !validTo.isBefore(dt) // validFrom_<=_dt_<=_validTo
          case (Some(dt0), None) => !dt0.isAfter(validTo) // dt0_<=_validTo
          case (Some(dt0), Some(dt1)) => !validFrom.isAfter(dt1) && !validTo.isBefore(dt0) // validFrom_<=_dt1_AND_dt0_<=_validTo
        }
      }

      if (include) {
        // filter on fields ... TBD
        List(Source(
          IDFGridConfig.typeName,
          Some(IDFGridConfig.name),
          None, // name n/a
          None, // country n/a
          None, // countryCode n/a
          None, // WMO ID n/a
          None, // point n/a
          None, // levels n/a
          Some(IDFGridConfig.validFrom),
          Some(IDFGridConfig.validTo),
          None, // municipid n/a
          None, // municipname n/a
          None, // countyid n/a
          None // countyname n/a
        ))
      } else {
        List[Source]()
      }
    }
  }


  def getSources(
    srcSpec: SourceSpecification, geometry: Option[String], validTime: Option[String], name: Option[String],
    country: Option[String], fields: Set[String]): List[Source] = {

    var sources = List[Source]()

    if (srcSpec.includeStationSources) { // type 1
      sources = sources ++ STInfoSysExec(srcSpec.stationNumbers, geometry, validTime, name, country, fields)
    }

    if (srcSpec.includeIdfGridSources && name.isEmpty && country.isEmpty) { // type 2
      sources = sources ++ GridIDFExec(srcSpec.idfGridNames, validTime, fields)
    }

    // type 3 ...


    sources
  }

}

// $COVERAGE-ON$
