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

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import com.github.nscala_time.time.Imports._
import java.net.URL
import no.met.data.{ApiConstants,BasicResponseData,ConfigUtil}
import no.met.data.format.json.BasicJsonFormat

import models._


/**
 * Creating a json representation of elements data
 */
object JsonFormat extends BasicJsonFormat {

  /* JsValue Writers */

  implicit val pointWrites: Writes[Point] = (
    (JsPath \ "@type").write[String] and 
    (JsPath \ "coordinates").write[Seq[Double]]
  )(unlift(Point.unapply))

  implicit val sourceWrites: Writes[Source] = (
    (JsPath \ "@type").write[String] and
    (JsPath \ "id").write[String] and
    (JsPath \ "name").write[String] and
    (JsPath \ "country").write[String] and
    (JsPath \ "wmoNumber").writeNullable[Int] and
    (JsPath \ "geo").writeNullable[Point] and
    (JsPath \ "level").writeNullable[Double] and
    (JsPath \ "levelUnit").writeNullable[String] and
    (JsPath \ "levelType").writeNullable[String] and
    (JsPath \ "validFrom").write[String] and
    (JsPath \ "validTo").writeNullable[String]
  )(unlift(Source.unapply))

  implicit val sourceResponseWrites: Writes[SourceResponse] = (
    (JsPath \ ApiConstants.CONTEXT_NAME).write[URL] and 
    (JsPath \ ApiConstants.OBJECT_TYPE_NAME).write[String] and
    (JsPath \ ApiConstants.API_VERSION_NAME).write[String] and
    (JsPath \ ApiConstants.LICENSE_NAME).write[URL] and
    (JsPath \ ApiConstants.CREATED_AT_NAME).write[DateTime] and
    (JsPath \ ApiConstants.QUERY_TIME_NAME).write[Duration] and
    (JsPath \ ApiConstants.CURRENT_ITEM_COUNT_NAME).write[Long] and
    (JsPath \ ApiConstants.ITEMS_PER_PAGE_NAME).write[Long] and
    (JsPath \ ApiConstants.OFFSET_NAME).write[Long] and
    (JsPath \ ApiConstants.TOTAL_ITEM_COUNT_NAME).write[Long] and
    (JsPath \ ApiConstants.NEXT_LINK_NAME).writeNullable[URL] and
    (JsPath \ ApiConstants.PREVIOUS_LINK_NAME).writeNullable[URL] and
    (JsPath \ ApiConstants.CURRENT_LINK_NAME).write[URL] and
    (JsPath \ ApiConstants.DATA_NAME).write[Seq[Source]]
  )(unlift(SourceResponse.unapply))
  
  /**
   * Create json representation of the given list
   * @param start Start time of the query processing.
   * @param elements The list to create a representation of.
   * @return json representation, as a string
   */
  def format[A](start: DateTime, sources: List[Source])(implicit request: Request[A]): String = {
    val size = sources.size
    val duration = new Duration(DateTime.now.getMillis() - start.getMillis())
    val response = new SourceResponse( new URL(ApiConstants.METAPI_CONTEXT),
                                       "SourceResponse",
                                       "v0",
                                        new URL(ApiConstants.METAPI_LICENSE),
                                        start,
                                        duration,
                                        size,
                                        size,
                                        0,
                                        size,
                                        None,
                                        None,
                                        new URL(ConfigUtil.urlStart + request.uri),
                                        sources)
    Json.prettyPrint(Json.toJson(response))
  }

}