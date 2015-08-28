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

import play.api.mvc._
import play.api.libs.json._
import com.github.nscala_time.time.Imports._
import no.met.stinfosys.{ Station, ResponseData }
import no.met.data.BasicResponseData
import no.met.data.format.json.BasicJsonFormat

/**
 * Creating a json representation of Stations data
 */
object JsonFormat extends BasicJsonFormat {

  implicit val StationWriter: Writes[Station] = new Writes[Station] {

    private def withoutValue(v: JsValue): Boolean = v match {
      case JsNull => true
      case JsString("") => true
      case _ => false
    }

    def writes(Station: Station): JsObject = {
      val js = Json.obj(
        "@type" -> "SensorSystem",
        "id" -> Station.sourceid,
        "name" -> Station.name,
        "country" -> Station.country, // not in spec but seems useful
        "wmo" -> Station.wmono,
        "geo" -> Json.obj(
          "@type" -> "Point",
          "coordinates" ->  Json.arr( Station.lat, Station.lon )),
        "level" -> "height_above_geoid",
        "levelValue" -> Station.height,
        "createdTime" -> Station.fromdate
        )
      JsObject(js.fields.filterNot(t => withoutValue(t._2)))
    }
  }

  implicit val responseDataWrites: Writes[ResponseData] = new Writes[ResponseData] {
    def writes(response: ResponseData): JsObject = {
      header(response.header) + ("data", Json.toJson(response.data))
    }
  }

  /**
   * Create json representation of the given list
   *
   * @param start Start time of the query processing.
   * @param Stations The list to create a representation of.
   * @return json representation, as a string
   */
  def format[A](start: DateTime, Stations: Traversable[Station])(implicit request: Request[A]): String = {
    val size = Stations.size
    val duration = new Duration(DateTime.now.getMillis() - start.getMillis())
    // Create json representation
    val header = BasicResponseData("Response", "Sources", "v0", duration, size, size, size, 0, None, None)
    val response = ResponseData(header, Stations)
    Json.prettyPrint(Json.toJson(response))
  }

}
