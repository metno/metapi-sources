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

package no.met.sources

import javax.inject.Singleton
import anorm.SQL
import play.api.db._
import play.Logger
import play.api.libs.ws._
import scala.concurrent._
import scala.util._
import java.sql.Connection
import anorm.NamedParameter.string
import anorm.sqlToSimple
import scala.annotation.tailrec
import anorm.NamedParameter
import no.met.stinfosys.Station

/**
 * Mocked implementation of StationDatabaseAccess class with static test data
 */
@Singleton
class MockStationDatabaseAccess extends StationDatabaseAccess {

  // scalastyle:off
  val mockSourcelist = List[Station](
    new Station("KN4200",    "KJELLER",              "Norge",               Some(1466),  Some(108), Some(59.9708),          Some(11.0383),            "2010-01-06"),
    new Station("KN18700",   "OSLO - BLINDERN",      "Norge",               Some(1492),  Some(94),  Some(59.9423),          Some(10.72),              "1941-01-01"),
    new Station("KN70740",   "STEINKJER",            "Norge",               None,        Some(10),  Some(64.02),            Some(11.5),               "1500-01-01"),
    new Station("KN76931",   "TROLL A",              "Norge",               Some(1309),  Some(128), Some(60.6435),          Some(3.7193),             "2010-12-01"),
    new Station("KN377200",  "HEATHROW",             "Storbritannia",       Some(3772),  Some(24),  Some(51.4791666666667), Some(-0.450546448087432), "2015-02-03"),
    new Station("KN401800",  "KEFLAVIKURFLUGVOLLUR", "Island",              Some(4018),  Some(52),  Some(63.9805555555556), Some(-22.5948087431694),  "2015-02-03"),
    new Station("KN2647700", "VELIKIE LUKI",         "Russland (i Europa)", Some(26477), Some(97),  Some(56.35),            Some(30.6166666666667),   "2011-08-14"),
    new Station("KN4794600", "OKINAWA",              "Japan",               Some(47946), None,      Some(26.5),             Some(127.9),              "2013-06-01")
  )
  // scalastyle:on

  val defaultLimit: Int = 100 // todo: set in constructor

  def getStations(sources: Array[String], types: Option[String], validtime: Option[String], bbox: Array[Double],
      fields: Option[String], limit: Option[Int], offset: Option[Int]): List[Station] = {

    mockSourcelist.
      filter(s => sources.length == 0 || sources.contains(s.sourceid.toUpperCase)).
      filter(s => bbox.length == 0    || s.lon.get >= bbox(0) && s.lat.get >= bbox(1) && s.lon.get <= bbox(2) && s.lat.get <= bbox(3))

  }

}
