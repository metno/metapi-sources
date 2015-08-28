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

package no.met.stinfosys

import javax.inject.Singleton
import anorm.SQL
import play.api.db._
import play.api.Play.current
import play.Logger
import play.api.libs.ws._
import scala.concurrent._
import scala.util._
import java.sql.Connection
import anorm.NamedParameter.string
import anorm.sqlToSimple
import scala.annotation.tailrec
import anorm.NamedParameter

//$COVERAGE-OFF$Not testing database queries

/**
 * Concrete implementation of StinfosysDatabaseAccess class, connecting to a real stinfosys
 * database.
 */
@Singleton
class StinfosysDatabaseAccess { // extends StinfosysAccess

  val getStationsQuery = """
        SELECT
       | 'KN'||stationid AS sourceid, s.name AS name, c.name AS country, wmono, hs, lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS fromdate
       |FROM
       | station s, country c
       |WHERE
       | c.countryid = s.countryid
       |ORDER BY
       | stationid
       |LIMIT {limit} OFFSET {offset}""".stripMargin

  val getStationsByIdQuery = """
        SELECT
       | 'KN'|| stationid AS sourceid, s.name AS name, c.name AS country, wmono, hs, lat, lon, TO_CHAR(fromtime, 'YYYY-MM-DD') AS fromdate
       |FROM
       | station s, country c
       |WHERE
       | 'KN'||stationid IN ({stations})
       | AND c.countryid = s.countryid
       |ORDER BY
       | stationid""".stripMargin

  val defaultLimit: Int = current.configuration.getString("db.stinfosys.defaultlimit").getOrElse("100").toInt

  def getStations(sources: Option[String], types: Option[String], validtime: Option[String],
      fields: Option[String], limit: Option[Int], offset: Option[Int]): List[Station] = {

    DB.withConnection("stinfosys") { implicit conn =>

      val _limit: NamedParameter = "limit" -> limit.getOrElse(defaultLimit)
      val _offset: NamedParameter = "offset" -> offset.getOrElse(0)
      // can't get Seq[NamedParameter] to work inside .on()
      val result = sources match {
        case Some("") => SQL(getStationsQuery).on(_limit, _offset)() // combine with None - FIXME
        case Some(ids) => {
          val idList = ids.split(",").map(_.trim).toList
          Logger.debug(idList.mkString(" "))
          SQL(getStationsByIdQuery).on( "stations" -> idList )() }
        case None  => SQL(getStationsQuery).on(_limit, _offset)()
      }
      result.map ( row =>
          Station(
              row[String]("sourceid"),
              row[String]("name"),
              row[String]("country"),
              row[Option[Int]]("wmono"),
              row[Option[Int]]("hs"),
              row[Option[Double]]("lat"),
              row[Option[Double]]("lon"),
              row[String]("fromdate")
          )
      ).toList

    }
  }

}

// $COVERAGE-ON$
