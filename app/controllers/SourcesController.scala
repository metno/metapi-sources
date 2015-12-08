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

package controllers

import play.api._
import play.api.mvc._
import util._
import javax.inject.Inject
import com.wordnik.swagger.annotations._
import javax.ws.rs.{ QueryParam, PathParam }
import com.github.nscala_time.time.Imports._
import no.met.sources._
import no.met.stinfosys._
import play.api.libs.json._
import no.met.sources.{ StationDatabaseAccess, JsonFormat }

@Api(value = "/sources", description = "Access data about sources of meteorological data")
class SourcesController @Inject()(stationDatabaseService: StationDatabaseAccess) extends Controller {
  /**
   * GET sources data from stinfosys
   * @sources a list of source IDs that the result should be limited to. If no sources are specified, all sources are returned
   * @types a list of source types that the result should be limited to
   * @validtime the validtime of data required. ISO-8601
   * @fields limit the data returned in the return format to only these variables or fields
   * @limit limit the number of answers. Default (and maximum) depends on user
   * @offset returns from this offset in the result set. Need to consider how this is implemented in real-time, distributed data set
   * @namespace sets the namespace used in the query and the return set
   *
   */
  @ApiOperation(
    nickname = "getSources",
    value = "Describe sources of metapi data (observation stations, models etc.)",
    response = classOf[String],
    httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "The request was successfully completed"),
    new ApiResponse(code = 400, message = "An error in the request"),
    new ApiResponse(code = 401, message = "The authentication credentials included with this request are missing or invalid"),
    new ApiResponse(code = 404, message = "No data was found for the specified ID"),
    new ApiResponse(code = 500, message = "The service encountered an unexpected server-side condition which prevented it from fulfilling the request")))
  def getSources( // scalastyle:ignore public.methods.have.type
      @ApiParam(value = "list of source IDs", required = false, allowMultiple = true)@QueryParam("sources") sources: Option[String],
      @ApiParam(value = "list of source types", required = false, allowableValues = "SensorSystem", defaultValue = "SensorSystem")@QueryParam("types") types: Option[String],
      @ApiParam(value = "the validtime of data required", required = false)@QueryParam("validtime") validtime: Option[String],
      @ApiParam(value = "get only sources located within this WSEN bounding box", required = false)@QueryParam("bbox") bbox: Option[String],
      @ApiParam(value = "limit the data returned in the return format to only these variables or fields", required = false, allowMultiple = true)@QueryParam("fields") fields: Option[String],
      @ApiParam(value = "limit the number of records returned", required = false, defaultValue = "100")@QueryParam("limit") limit: Option[Int],
      @ApiParam(value = "returns from this offset in the result set", required = false)@QueryParam("offset") offset: Option[Int],
      @ApiParam(value = "sets the namespace used in the query and the return set", required = false)@QueryParam("namespace") namespace: Option[String],
      @ApiParam(value = "output format", required = true, allowableValues = "jsonld",
        defaultValue = "jsonld")@PathParam("format") format: String) = no.met.security.AuthorizedAction {
    implicit request =>
    // Start the clock
    val start = DateTime.now(DateTimeZone.UTC)
    Try {
      //if (types == "SensorSystem") { // suggest we default to this and use sourceid prefix to determine type when possible

      val bboxList : Array[Double] = bbox match {
        case Some(bbox) => bbox.split(",").map(_.toDouble) // TODO - check that exactly 0 or 4
        case _ => Array()
      }
      if (bboxList.length > 0 && bboxList.length != 4) throw new Exception("bbox parameter must contain exactly 4 comma-separated numbers")

      val sourceList : Array[String] = sources match {
        case Some(sources) => sources.toUpperCase.split(",").map(_.trim)
        case _ => Array()
      }

      stationDatabaseService.getStations(sourceList, types, validtime, bboxList, fields, limit, offset)

    } match {
      case Success(data) =>
      if (data isEmpty) {
        NotFound("Found no data for sources " + sources.getOrElse("<all>"))
      } else {
        implicit val sourceFormat = Json.format[Station]
        format.toLowerCase() match {
          case "jsonld" => Ok(JsonFormat.format(start, data)) as "application/vnd.no.met.data.sources-v0+json"
          case x        => BadRequest(s"Invalid output format: $x")
        }
      }
      //} else {
        //NotFound("Only type SensorSystem currently implemented")
      //}
      case Failure(x) => BadRequest(x getLocalizedMessage)
    }
  }
}
