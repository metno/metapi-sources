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
import play.Logger
import anorm._
import anorm.SqlParser._
import javax.inject.Singleton
import scala.language.postfixOps
import no.met.data._
import no.met.data.AnormUtil._
import no.met.geometry._
import models._
import org.joda.time.format.{ DateTimeFormatter, DateTimeFormat, ISODateTimeFormat }
import org.joda.time.DateTime
import util.{ Try, Failure, Success }


//$COVERAGE-OFF$ Not testing database queries

/** Concrete implementation of SourceAccess class, connecting to the MET API's STInfoSys clone database.
 */
@Singleton
class ProdSourceAccess extends SourceAccess {

  private object STInfoSysExec {

    private def getSelectQuery(fields: Set[String]): String = {
      val legalFields = Set(
        "type", "name", "country", "countrycode", "wmoidentifier", "geometry", "level", "validfrom", "validto",
        "municipalityid", "municipalityname", "countyid", "countyname", "stationholders", "externalids", "icaocodes", "shipcodes")
      val illegalFields = fields -- legalFields
      if (illegalFields.nonEmpty) {
        throw new BadRequestException(
          "Invalid fields in the query parameter: " + illegalFields.mkString(","),
          Some(s"Supported fields: ${legalFields.mkString(", ")}"))
      }
      val requiredFields = Set("id")

      val fieldStr = (requiredFields ++ fields).mkString(", ")
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

    private def getRestrictedStations: Set[String] = {
      val parser: RowParser[String] = {
        get[Option[String]]("stationId") map {
          case stationId => stationId.get
        }
      }
      val query = "SELECT DISTINCT 'SN' || stationid AS stationId FROM message_policy WHERE permitid IN (3, 4, 6)"
      DB.withConnection("sources") { implicit connection =>
        SQL(query).as(parser *).toSet
      }
    }

    private def getStationHolders: Map[String, List[String]] = {
      val parser: RowParser[(String, List[String])] = {
        get[Option[String]]("stationId") ~
          get[Option[List[String]]]("stationHolders") map {
          case stationId~stationHolders => (stationId.get, stationHolders.get)
        }
      }
      val query =
        """
          |SELECT 'SN' || stationId AS stationId,
          |  (SELECT array_agg(name)
          |   FROM organisation
          |   WHERE organisationid=ANY(stationHolderIds)
          |     AND totime IS NULL /* to get current organisation name only */
          |  ) AS stationHolders
          |FROM (
          |  SELECT os.stationid AS stationId,
          |    array_agg(DISTINCT os.organisationid) AS stationHolderIds
          |  FROM organisation_station os, organisation o
          |  WHERE os.organisationid=o.organisationid
          |    AND os.roleid=100 /* code for station holder role */
          |    AND os.totime IS NULL /* to get current station holder only */
          |  GROUP BY os.stationid
          |) t1
        """.stripMargin
      DB.withConnection("sources") { implicit connection =>
        SQL(query).as(parser *).toMap
      }
    }

    private def getExternalIds(networkId: Option[Int] = None): Map[String, List[String]] = {
      val parser: RowParser[(String, List[String])] = {
        get[Option[String]]("stationId") ~
          get[Option[List[String]]]("externalIds") map {
          case stationId~externalIds => (stationId.get, externalIds.get)
        }
      }
      val query =
        s"""
          |SELECT 'SN' || s.stationid AS stationId,
          |  array_agg(DISTINCT ns.external_stationcode) AS externalIds
          |FROM station s, network_station ns
          |WHERE s.stationid=ns.stationid
          |  AND ns.external_stationcode IS NOT NULL
          |  AND ns.external_stationcode != ''
          |  ${if (networkId.nonEmpty) s"AND ns.networkid=${networkId.get}" else ""}
          |GROUP BY s.stationid
        """.stripMargin
      DB.withConnection("sources") { implicit connection =>
        SQL(query).as(parser *).toMap
      }
    }

    private val parser: RowParser[Source] = {
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
            Some("SensorSystem"),
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
            cntname,
            None, // station holders - filled in later if applicable
            None, // external IDs - ditto
            None, // ICAO codes - ditto
            None // ship codes - ditto
          )
        }
      }
    }

    // scalastyle:off method.length
    def apply(
      ids: Seq[String], geometry: Option[String], validTime: Option[String], name: Option[String],
      country: Option[String], stationHolder: Option[String], externalId: Option[String], icaoCode: Option[String], shipCode: Option[String],
      fields: Set[String]): List[Source] = {

      val innerSelectQ = """
         NULL AS type,
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
         (CASE WHEN 0 < m.municipid AND m.municipid < 10000 THEN (SELECT name FROM municip WHERE municipid = m.municipid / 100) ELSE NULL END) AS countyname,
         NULL AS stationholders,
         NULL AS externalids,
         NULL AS icaocodes,
         NULL AS shipcodes
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

      val query = if (geometry.isEmpty) {
        s"""
        |SELECT
          |$selectQ
        |FROM
          |(SELECT $innerSelectQ
          |FROM
            |station s, country c, municip m
          |WHERE
            |$idsQ
            |AND c.countryid = s.countryid
            |AND m.municipid = (CASE WHEN s.municipid IS NULL THEN 0 ELSE s.municipid END)
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
            |(SELECT $innerSelectQ
            |FROM
              |station s, country c, municip m
            |WHERE
              |$idsQ
              |AND c.countryid = s.countryid
              |AND m.municipid = (CASE WHEN s.municipid IS NULL THEN 0 ELSE s.municipid END)
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
            |(SELECT $innerSelectQ
            |FROM
              |station s, country c, municip m
            |WHERE
              |$idsQ
              |AND c.countryid = s.countryid
              |AND m.municipid = (CASE WHEN s.municipid IS NULL THEN 0 ELSE s.municipid END)
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
        val nameList = if (name.isEmpty) List[String]() else List[String](replaceWildcards(name.get))
        val countryList = if (country.isEmpty) List[String]() else List[String](replaceWildcards(country.get))
        val result = SQL(insertPlaceholders(query, List(("name", nameList.size), ("country", countryList.size))))
          .on(onArg(List(("name", nameList), ("country", countryList))): _*)
          .as( parser * )

        val restricted = getRestrictedStations
        val statHolders = getStationHolders
        val extIdsAll = getExternalIds()
// scalastyle:off magic.number
        val extIdsICAO = getExternalIds(Some(101)) // by definition
        val extIdsShip = getExternalIds(Some(6)) // by definition
// scalastyle:on magic.number
        val showType = !selectQ.contains("NULL AS type")
        val showStatHolders = !selectQ.contains("NULL AS stationholders")
        val showExtIdsAll = !selectQ.contains("NULL AS externalids")
        val showExtIdsICAO = !selectQ.contains("NULL AS icaocodes")
        val showExtIdsShip = !selectQ.contains("NULL AS shipcodes")

        result
          .filter(s => !restricted(s.id.get)) // remove restricted stations
          //
          .map(s => Try(statHolders(s.id.get)) match { // insert any station holders
            case Success(x) => s.copy(stationHolders = Some(x)) // set station holders
            case _ => s // leave unmodified
          })
          .filter(s => { // remove stations that don't match a specified station holder
            stationHolder.isEmpty ||
              (s.stationHolders.nonEmpty && s.stationHolders.get.exists(x => x.toLowerCase.matches(stationHolder.get.toLowerCase.replace("*", ".*"))))
          })
          //
          .map(s => Try(extIdsAll(s.id.get)) match { // insert any external IDs
            case Success(x) => s.copy(externalIds = Some(x)) // set external IDs
            case _ => s // leave unmodified
          })
          .filter(s => { // remove stations that don't match a specified external ID
            externalId.isEmpty ||
              (s.externalIds.nonEmpty && s.externalIds.get.exists(x => x.toLowerCase.matches(externalId.get.toLowerCase.replace("*", ".*"))))
          })
          //
          .map(s => Try(extIdsICAO(s.id.get)) match { // insert any ICAO codes
            case Success(x) => s.copy(icaoCodes = Some(x)) // set ICAO codes
            case _ => s // leave unmodified
          })
          .filter(s => { // remove stations that don't match a specified ICAO code
            icaoCode.isEmpty ||
              (s.icaoCodes.nonEmpty && s.icaoCodes.get.exists(x => x.toLowerCase.matches(icaoCode.get.toLowerCase.replace("*", ".*"))))
          })
          //
          .map(s => Try(extIdsShip(s.id.get)) match { // insert any ship codes
            case Success(x) => s.copy(shipCodes = Some(x)) // set ship codes
            case _ => s // leave unmodified
          })
          .filter(s => { // remove stations that don't match a specified ship code
            shipCode.isEmpty ||
              (s.shipCodes.nonEmpty && s.shipCodes.get.exists(x => x.toLowerCase.matches(shipCode.get.toLowerCase.replace("*", ".*"))))
          })
          //
          .map(s => s.copy( // remove fields from output as required
            sType = if (showType) s.sType else None,
            stationHolders = if (showStatHolders) s.stationHolders else None,
            externalIds = if (showExtIdsAll) s.externalIds else None,
            icaoCodes = if (showExtIdsICAO) s.icaoCodes else None,
            shipCodes = if (showExtIdsShip) s.shipCodes else None
          ))
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
          Some(IDFGridConfig.typeName),
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
          None, // countyname n/a
          None, // stationHolders n/a
          None, // externalIds n/a
          None, // icaoCodes n/a
          None  // shipCodes n/a
        ))
      } else {
        List[Source]()
      }
    }
  }


  def getSources(
    srcSpec: SourceSpecification, geometry: Option[String], validTime: Option[String], name: Option[String],
    country: Option[String], stationHolder: Option[String], externalId: Option[String], icaoCode: Option[String], shipCode: Option[String],
    fields: Set[String]): List[Source] = {

    var sources = List[Source]()

    if (srcSpec.includeStationSources) { // type 1
      sources = sources ++ STInfoSysExec(srcSpec.stationNumbers, geometry, validTime, name, country, stationHolder, externalId, icaoCode, shipCode, fields)
    }

    if (srcSpec.includeIdfGridSources && name.isEmpty && country.isEmpty) { // type 2
      sources = sources ++ GridIDFExec(srcSpec.idfGridNames, validTime, fields)
    }

    // type 3 ...


    sources
  }

}

// $COVERAGE-ON$
