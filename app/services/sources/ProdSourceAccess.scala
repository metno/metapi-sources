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
        get[Option[String]]("validto") map {
        case sourceid~name~country~countryCode~wmono~hs~lat~lon~fromDate~toDate =>
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
            toDate)
      }
    }

    private def getSelectQuery(fields: Set[String]): String = {
      val legalFields = Set("id", "name", "country", "countrycode", "wmoidentifier", "geometry", "level", "validfrom", "validto")
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

    // Converts a string to use '%' for trailing wildcard instead of '*'.
    private def replaceTrailingWildcard(s: String): String = {
      if (s.nonEmpty && (s.last == '*')) s.updated(s.length - 1, '%') else s
    }

    // Generates a WHERE clause using a prepared filter that matches a single attribute.
    private def preparedFilterQ(attr: String, value: Option[String], placeholder: String): String = {
      value match {
        case Some(s) if s.nonEmpty => s"(lower($attr) LIKE lower({$placeholder}))"
        case _ => "TRUE"
      }
    }

    // Generates a WHERE clause using a prepared filter that matches any of two attributes.
    private def preparedFilterQ2(attr1: String, attr2: String, value: Option[String], placeholder: String): String = {
      value match {
        case Some(s) if s.nonEmpty => s"(${preparedFilterQ(attr1, value, placeholder)} OR ${preparedFilterQ(attr2, value, placeholder)})"
        case _ => "TRUE"
      }
    }

    // scalastyle:off method.length
    def apply(
      ids: Seq[String], geometry: Option[String], validTime: Option[String], name: Option[String],
      country: Option[String], fields: Set[String]): List[Source] = {

      val selectQ = if (fields.isEmpty) "*" else getSelectQuery(fields)

      // Filter by source id
      val idsQ = if (ids.nonEmpty) {
        val idStr = SourceSpecification.stationWhereClause(ids, "stationid", None)
        s"($idStr)"
      } else {
        "TRUE"
      }

      val validTimeQ = getValidTimeQuery(validTime)
      val nameQ = preparedFilterQ("s.name", name, "nameFilter")
      val countryQ = preparedFilterQ2("c.name", "c.alias", country, "countryFilter")

      val query = if (geometry.isEmpty) {
        s"""
        |SELECT
          |$selectQ
        |FROM
          |(SELECT
            |'SN'|| stationid AS id, s.name AS name, c.name AS country, c.alias AS countryCode, wmono AS wmoidentifier, hs AS level,
            |lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS validfrom, TO_CHAR(totime, 'YYYY-MM-DD') AS validto
          |FROM
            |station s, country c
          |WHERE
            |$idsQ
            |AND c.countryid = s.countryid
            |AND $validTimeQ
            |AND $nameQ
            |AND $countryQ
          |ORDER BY
            |id) t0""".stripMargin
      } else {
        val geom = Geometry.decode(geometry.get)
        if (geom.isInterpolated) {
          s"""
          |SELECT
            |$selectQ
          |FROM
            |(SELECT
              |'SN'|| stationid AS id, s.name AS name, c.name AS country, c.alias AS countryCode, wmono AS wmoidentifier, hs AS level,
              |lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS validfrom, TO_CHAR(totime, 'YYYY-MM-DD') AS validto
            |FROM
              |station s, country c
            |WHERE
              |$idsQ
              |AND c.countryid = s.countryid
              |AND $validTimeQ
              |AND $nameQ
              |AND $countryQ
            |ORDER BY
              |ST_SetSRID(ST_MakePoint(lon, lat),4326) <-> ST_GeomFromText('${geom.asWkt}',4326), id
            |LIMIT 1) t0""".stripMargin
        } else {
          s"""
          |SELECT
            |$selectQ
          |FROM
            |(SELECT
              |'SN'|| stationid AS id, s.name AS name, c.name AS country, c.alias AS countryCode, wmono AS wmoidentifier, hs AS level,
              |lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS validfrom, TO_CHAR(totime, 'YYYY-MM-DD') AS validto
            |FROM
              |station s, country c
            |WHERE
              |$idsQ
              |AND c.countryid = s.countryid
              |AND $validTimeQ
              |AND $nameQ
              |AND $countryQ
              |AND ST_WITHIN(ST_SetSRID(ST_MakePoint(lon, lat),4326), ST_GeomFromText('${geom.asWkt}',4326))
            |ORDER BY
              |id) t0""".stripMargin
        }
      }

      //Logger.debug(query)

      DB.withConnection("sources") { implicit connection =>
        SQL(query).on(
          "nameFilter" -> replaceTrailingWildcard(name.getOrElse("")),
          "countryFilter" -> replaceTrailingWildcard(country.getOrElse(""))
        ).as( parser * )
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
          Some(IDFGridConfig.validTo)
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
