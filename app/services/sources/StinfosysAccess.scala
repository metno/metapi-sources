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
import models._

//$COVERAGE-OFF$Not testing database queries

/** Concrete implementation of SourceAccess class, connecting to the MET API's STInfoSys clone database.
 */
@Singleton
class StinfosysAccess extends SourceAccess {

  val parser: RowParser[Source] = {
    get[String]("sourceid") ~
    get[String]("name") ~
    get[String]("country") ~
    get[Option[Int]]("wmono") ~
    get[Option[Double]]("hs") ~
    get[Double]("lat") ~
    get[Double]("lon") ~
    get[String]("fromdate") ~
    get[Option[String]]("todate") map {
      case sourceid~name~country~wmono~hs~lat~lon~fromDate~toDate => Source("SensorSystem", sourceid, name, country, wmono, Some(SPoint("Point", Array(lon, lat))), hs, Some("m"), Some("height_above_ground"), fromDate, toDate)
    }
  }

  def getStations(ids: Array[String], types: Option[String], bbox: Array[Double], validTime: Option[String], fields: Option[String]): List[Source] = {

    DB.withConnection("sources") { implicit conn =>

      //val _limit: NamedParameter = "limit" -> limit.getOrElse(defaultLimit)
      //val _offset: NamedParameter = "offset" -> offset.getOrElse(0)
      // can't get Seq[NamedParameter] to work inside .on()

      val latlonclause = if (bbox.length > 0) {
          // coords have been cast to Double so should be safe from XSS attacks
          s"(lon BETWEEN ${bbox(0)} AND ${bbox(2)}) AND (lat BETWEEN ${bbox(1)} AND ${bbox(3)}) "
        } else { "" }
      
      val getStationsQuery = s"""
            SELECT
           | 'SN'||stationid AS sourceid, s.name AS name, c.name AS country, wmono, hs, lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS fromdate, TO_CHAR(totime, 'YYYY-MM-DD') AS todate
           |FROM
           | station s, country c
           |WHERE
           | c.countryid = s.countryid
           | AND s.totime is null
           | ${latlonclause}
           |ORDER BY
           | stationid
           |LIMIT {limit} OFFSET {offset}""".stripMargin

      val getStationsByIdQuery = s"""
            SELECT
           | 'SN'|| stationid AS sourceid, s.name AS name, c.name AS country, wmono, hs, lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS fromdate, TO_CHAR(totime, 'YYYY-MM-DD') AS todate
           |FROM
           | station s, country c
           |WHERE
           | 'KN'||stationid IN ({stations})
           | AND c.countryid = s.countryid
           | AND s.totime is null
           | AND ${latlonclause}
           |ORDER BY
           | stationid""".stripMargin

      Logger.debug(getStationsQuery)

      val result = if (ids.length > 0) {
        SQL(getStationsByIdQuery).on( "stations" -> ids.toList ).as( parser * )
      } else {
        SQL(getStationsQuery).as( parser * )
      }

      result

    }
  }

}

// $COVERAGE-ON$
