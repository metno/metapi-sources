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

import services.frequencies.{QueryParameters, ProdIDFAccess}
import models.RainfallIDFSource

//$COVERAGE-OFF$

// Object to determine if IDF data is available for a given station source.
object StationSourceHasIDF {

  private var stationsWithIDF: Set[String] = Set[String]() // IDs of station sources that provide IDF data
  private var idfAccess = new ProdIDFAccess
  private var lastRefresh: Long = -1 // time since last refresh (secs since 1970)
  private val refreshPause = 3600 * 24 // don't refresh more often than this number of seconds

  private def nowSecs: Long = System.currentTimeMillis / 1000  // current time since 1970 in seconds.

  private def refresh() = {
    val idfStationSources: List[RainfallIDFSource] = idfAccess.idfSources(QueryParameters(None, Some("SensorSystem"))
    stationsWithIDF = idfStationSources.map(s => s.sourceId).toSet
    lastRefresh = nowSecs
  }

  def apply(stationId: String): Boolean = {
    if ((nowSecs - lastRefresh) > refreshPause) refresh()
    stationsWithIDF.contains(stationId)
  }
}

//$COVERAGE-ON$
