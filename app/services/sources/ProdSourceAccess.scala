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

  private def getSelectQuery(fields: Set[String]) : String = {
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
            |AND s.totime is null
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
              |$idsQ AND
              |c.countryid = s.countryid AND
              |s.totime is null AND
              |$nameQ AND
              |$countryQ
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
              |$idsQ AND
              |c.countryid = s.countryid AND
              |s.totime is null AND
              |$nameQ AND
              |$countryQ AND
              |ST_WITHIN(ST_SetSRID(ST_MakePoint(lon, lat),4326), ST_GeomFromText('${geom.asWkt}',4326))
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

    def apply(idfGridIds: Seq[String], fields: Set[String]): List[Source] = {

      assert(idfGridIds.isEmpty || ((idfGridIds.length == 1) && (idfGridIds(0) == IDFGridConfig.name))) // for now

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
      sources = sources ++ GridIDFExec(srcSpec.idfGridNames, fields)
    }

    // type 3 ...


    sources
  }

}

// $COVERAGE-ON$
