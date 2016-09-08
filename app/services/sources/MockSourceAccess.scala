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

import javax.inject.Singleton
import play.Logger
import no.met.geometry.Point
import models._

/**
 * Mocked implementation of StationDatabaseAccess class with static test data
 */
@Singleton
class MockSourceAccess extends SourceAccess {

  // scalastyle:off
  val mockSourcelist = List[Source](
    new Source("SensorSystem",  "4200",    "KJELLER",              "Norge",               Some(1466),  Some(Point(coordinates=Seq(11.0383, 59.9708))),                      Some(108),  Some("m"),  Some("height_above_geometryid"),  "2010-01-06",  None),
    new Source("SensorSystem",  "18700",   "OSLO - BLINDERN",      "Norge",               Some(1492),  Some(Point(coordinates=Seq(10.72, 59.9423))),                        Some(94),   Some("m"),  Some("height_above_geometryid"),  "1941-01-01",  None),
    new Source("SensorSystem",  "70740",   "STEINKJER",            "Norge",               None,        Some(Point(coordinates=Seq(11.5, 64.02))),                           Some(10),   Some("m"),  Some("height_above_geometryid"),  "1500-01-01",  None),
    new Source("SensorSystem",  "76931",   "TROLL A",              "Norge",               Some(1309),  Some(Point(coordinates=Seq(3.7193, 60.6435))),                       Some(128),  Some("m"),  Some("height_above_geometryid"),  "2010-12-01",  None),
    new Source("SensorSystem",  "377200",  "HEATHROW",             "Storbritannia",       Some(3772),  Some(Point(coordinates=Seq(-0.450546448087432, 51.4791666666667))),  Some(24),   Some("m"),  Some("height_above_geometryid"),  "2015-02-03",  None),
    new Source("SensorSystem",  "401800",  "KEFLAVIKURFLUGVOLLUR", "Island",              Some(4018),  Some(Point(coordinates=Seq(-22.5948087431694, 63.9805555555556))),   Some(52),   Some("m"),  Some("height_above_geometryid"),  "2015-02-03",  None),
    new Source("SensorSystem",  "2647700", "VELIKIE LUKI",         "Russland (i Europa)", Some(26477), Some(Point(coordinates=Seq(30.6166666666667, 56.35))),               Some(97),   Some("m"),  Some("height_above_geometryid"),  "2011-08-14",  None),
    new Source("SensorSystem",  "4794600", "OKINAWA",              "Japan",               Some(47946), Some(Point(coordinates=Seq(127.9, 26.5))),                           None,       Some("m"),  Some("height_above_geometryid"),  "2013-06-01",  None)
  )
  // scalastyle:on

  def getStations(ids: Seq[String], types: Option[String], geometry: Option[String], validTime: Option[String], fields: Option[String]): List[Source] = {
    mockSourcelist.
      filter(s => ids.length == 0 || ids.contains(s.id))
    
  }

}
