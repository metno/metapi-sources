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
import no.met.stinfosys._

//$COVERAGE-OFF$Not testing database queries

/**
 * Concrete implementation of StinfosysDatabaseAccess class, connecting to a real stinfosys
 * database.
 */
@Singleton
class StinfosysDatabaseAccess extends StationDatabaseAccess {

  val parser: RowParser[Station] = {
    get[String]("sourceid") ~
    get[String]("name") ~
    get[String]("country") ~
    get[Option[Int]]("wmono") ~
    get[Option[Int]]("hs") ~
    get[Option[Double]]("lat") ~
    get[Option[Double]]("lon") ~
    get[String]("fromdate") map {
      case sourceid~name~country~wmono~hs~lat~lon~fromdate => Station(sourceid, name, country, wmono, hs, lat, lon, fromdate)
    }
  }
  
  val defaultLimit: Int = 100; // todo: set in constructor

  def getStations(sources: Array[String], types: Option[String], validtime: Option[String], bbox: Array[Double],
      fields: Option[String], limit: Option[Int], offset: Option[Int]): List[Station] = {

    DB.withConnection("sources") { implicit conn =>

      val _limit: NamedParameter = "limit" -> limit.getOrElse(defaultLimit)
      val _offset: NamedParameter = "offset" -> offset.getOrElse(0)
      // can't get Seq[NamedParameter] to work inside .on()

      val latlonclause = if (bbox.length > 0) {
          // coords have been cast to Double so should be safe from XSS attacks
          s" AND (lon BETWEEN ${bbox(0)} AND ${bbox(2)}) AND (lat BETWEEN ${bbox(1)} AND ${bbox(3)}) "
        } else { "" }
      
      val getStationsQuery = s"""
            SELECT
           | 'KN'||stationid AS sourceid, s.name AS name, c.name AS country, wmono, hs, lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS fromdate
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
           | 'KN'|| stationid AS sourceid, s.name AS name, c.name AS country, wmono, hs, lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS fromdate
           |FROM
           | station s, country c
           |WHERE
           | 'KN'||stationid IN ({stations})
           | AND c.countryid = s.countryid
           | AND s.totime is null
           | ${latlonclause}
           |ORDER BY
           | stationid""".stripMargin

      Logger.debug(getStationsQuery)

      val result = if (sources.length > 0) {
        SQL(getStationsByIdQuery).on( "stations" -> sources.toList ).as( parser * )
      } else {
        SQL(getStationsQuery).on(_limit, _offset).as( parser * )
      }

      result

    }
  }

}

// $COVERAGE-ON$
