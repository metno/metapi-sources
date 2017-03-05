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
import no.met.geometry._
import no.met.data._
import models._

/**
 * Mocked implementation of StationDatabaseAccess class with static test data
 */
@Singleton
class MockSourceAccess extends SourceAccess {

  // scalastyle:off
  val stationSources = List[Source]( // type 1
    new Source(StationConfig.typeName, Some("4200"),             Some("KJELLER"),              Some("Norge"),               Some("NO"), Some(1466),  Some(Point(coordinates=Seq(11.0383, 59.9708))),                     Some(Seq(Level(Some("height_above_geometryid"),Some(108),Some("m"),None))), Some(false), Some("2010-01-06"), None),
    new Source(StationConfig.typeName, Some("18700"),            Some("OSLO - BLINDERN"),      Some("Norge"),               Some("NO"), Some(1492),  Some(Point(coordinates=Seq(10.72, 59.9423))),                       Some(Seq(Level(Some("height_above_geometryid"),Some(108),Some("m"),None))), Some(false), Some("1941-01-01"), None),
    new Source(StationConfig.typeName, Some("70740"),            Some("STEINKJER"),            Some("Norge"),               Some("NO"), None,        Some(Point(coordinates=Seq(11.5, 64.02))),                          Some(Seq(Level(Some("height_above_geometryid"),Some(108),Some("m"),None))), Some(false), Some("1500-01-01"), None),
    new Source(StationConfig.typeName, Some("76931"),            Some("TROLL A"),              Some("Norge"),               Some("NO"), Some(1309),  Some(Point(coordinates=Seq(3.7193, 60.6435))),                      Some(Seq(Level(Some("height_above_geometryid"),Some(108),Some("m"),None))), Some(false), Some("2010-12-01"), None),
    new Source(StationConfig.typeName, Some("377200"),           Some("HEATHROW"),             Some("Storbritannia"),       Some("GB"), Some(3772),  Some(Point(coordinates=Seq(-0.450546448087432, 51.4791666666667))), Some(Seq(Level(Some("height_above_geometryid"),Some(108),Some("m"),None))), Some(false), Some("2015-02-03"), None),
    new Source(StationConfig.typeName, Some("401800"),           Some("KEFLAVIKURFLUGVOLLUR"), Some("Island"),              Some("IS"), Some(4018),  Some(Point(coordinates=Seq(-22.5948087431694, 63.9805555555556))),  Some(Seq(Level(Some("height_above_geometryid"),Some(108),Some("m"),None))), Some(false), Some("2015-02-03"), None),
    new Source(StationConfig.typeName, Some("2647700"),          Some("VELIKIE LUKI"),         Some("Russland (i Europa)"), Some("RU"), Some(26477), Some(Point(coordinates=Seq(30.6166666666667, 56.35))),              Some(Seq(Level(Some("height_above_geometryid"),Some(108),Some("m"),None))), Some(false), Some("2011-08-14"), None),
    new Source(StationConfig.typeName, Some("4794600"),          Some("OKINAWA"),              Some("Japan"),               Some("JP"), Some(47946), Some(Point(coordinates=Seq(127.9, 26.5))),                          None,                                                                       Some(false), Some("2013-06-01"), None)
  )
  val idfGridSources = List[Source]( // type 2
    new Source(IDFGridConfig.typeName, Some(IDFGridConfig.name), None, None, None, None, None, None, Some(true), None, None)
  )
  // scalastyle:on

  def getSources(
    srcSpec: SourceSpecification, geometry: Option[String], validTime: Option[String], name: Option[String],
    country: Option[String], fields: Set[String]): List[Source] = {

    var sources = List[Source]()

    if (srcSpec.includeStationSources) { // type 1
      val stationIds = srcSpec.stationNumbers
      sources = sources ++ stationSources.filter(s => stationIds.isEmpty || stationIds.contains(s.id.get))
    }

    if (srcSpec.includeIdfGridSources) { // type 2
      val idfGridIds = srcSpec.idfGridNames
      sources = sources ++ idfGridSources.filter(s => idfGridIds.isEmpty || idfGridIds.contains(s.id.get))
    }

    // add more types here

    sources

    //mockSourcelist.filter(s => srcSpec.stationNumbers.isEmpty || srcSpec.stationNumbers.contains(s.id.get))
  }

}
