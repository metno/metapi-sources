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

//$COVERAGE-OFF$Not testing database queries

/** Concrete implementation of SourceAccess class, connecting to the MET API's STInfoSys clone database.
 */
@Singleton
class StinfosysAccess extends SourceAccess {

  val parser: RowParser[Source] = {
    get[Option[String]]("id") ~
    get[Option[String]]("name") ~
    get[Option[String]]("country") ~
    get[Option[Int]]("wmoidentifier") ~
    get[Option[Double]]("level") ~
    get[Option[Double]]("lat") ~
    get[Option[Double]]("lon") ~
    get[Option[String]]("validfrom") ~
    get[Option[String]]("validto") map {
      case sourceid~name~country~wmono~hs~lat~lon~fromDate~toDate => 
        Source("SensorPlatform",
               sourceid,
               name,
               country,
               wmono,
               if (lon.isEmpty||lat.isEmpty) None else Some(Point(coordinates=Seq(lon.get, lat.get))),
               if (hs.isEmpty) None else Some(Seq(Level(Some("height_above_ground"), hs, Some("m"), None))),
               fromDate,
               toDate)
    }
  }

  private def getSelectQuery(fields: Set[String]) : String = {
    val legalFields = Set("id", "name", "country", "wmoidentifier", "geometry", "level", "validfrom", "validto")
    val illegalFields = fields -- legalFields
    if (!illegalFields.isEmpty) throw new BadRequestException("Invalid fields in the query parameter: " + illegalFields.mkString(","))
    val fieldStr = fields.mkString(", ")
      .replace("geometry", "lat, lon")
    val missing = legalFields -- fields
    if (missing.isEmpty)
      fieldStr
    else {
      val missingStr = missing.map( x => "NULL AS " + x ).mkString(", ").replace("NULL AS geometry", "NULL AS lat, NULL AS LON")
      fieldStr + "," + missingStr
    }
  }

  def getStations(ids: Seq[String], types: Option[String], geometry: Option[String], validTime: Option[String], fields: Set[String]): List[Source] = {
    val selectQ = if (fields.isEmpty) "*" else getSelectQuery(fields)
    // Filter by source id
    val idsQ = if (ids.length > 0) {
      val idList = ids.mkString(",")
      s"stationid IN (${idList})"
    } else "TRUE"
    val query = if (geometry.isEmpty) {
      s"""
      |SELECT
        |$selectQ
      |FROM
        |(SELECT
          |'SN'|| stationid AS id, s.name AS name, c.name AS country, wmono AS wmoidentifier, hs AS level, lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS validfrom, TO_CHAR(totime, 'YYYY-MM-DD') AS validto
        |FROM
          |station s, country c
        |WHERE
          |$idsQ
          |AND c.countryid = s.countryid
          |AND s.totime is null
        |ORDER BY
          |id) t0""".stripMargin
    }
    else {
      val geom = Geometry.decode(geometry.get)
      if (geom.isInterpolated) {
        s"""
        |SELECT
          |$selectQ
        |FROM
          |(SELECT
            |'SN'|| stationid AS id, s.name AS name, c.name AS country, wmono AS wmoidentifier, hs AS level, lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS validfrom, TO_CHAR(totime, 'YYYY-MM-DD') AS validto
          |FROM
            |station s, country c
          |WHERE
            |$idsQ AND
            |c.countryid = s.countryid AND
            |s.totime is null
          |ORDER BY
            |ST_SetSRID(ST_MakePoint(lon, lat),4326) <-> ST_GeomFromText('${geom.asWkt}',4326), id
          |LIMIT 1) t0""".stripMargin
      }
      else {
        s"""
        |SELECT
          |$selectQ
        |FROM
          |(SELECT
            |'SN'|| stationid AS id, s.name AS name, c.name AS country, wmono AS wmoidentifier, hs AS level, lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS validfrom, TO_CHAR(totime, 'YYYY-MM-DD') AS validto
          |FROM
            | station s, country c
          |WHERE
            |$idsQ AND
            |c.countryid = s.countryid AND
            |s.totime is null AND
            |ST_WITHIN(ST_SetSRID(ST_MakePoint(lon, lat),4326), ST_GeomFromText('${geom.asWkt}',4326))
          |ORDER BY
            |id) t0""".stripMargin
      }
    }

    Logger.debug(query)
  
    DB.withConnection("sources") { implicit connection =>
      SQL(query).as( parser * )
    }

  }

}

// $COVERAGE-ON$
