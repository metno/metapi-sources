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
import play.api.http.Status._
import com.github.nscala_time.time.Imports._
import javax.inject.Inject
import io.swagger.annotations._
import scala.language.postfixOps
import util._
import models.Source
import services.sources.{ SourceAccess, JsonFormat }

// scalastyle:off magic.number

@Api(value = "sources")
class SourcesController @Inject()(sourceAccess: SourceAccess) extends Controller {

  @ApiOperation(
    value = "Get metada for MET API sources.",
    notes = "Get metadata for the source entitites defined in the MET API. Use the query parameters to filter the set of sources returned. Leave the query parameters blank to select **all** sources.",
    response = classOf[models.SourceResponse],
    httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 400, message = "Invalid parameter value or malformed request."),
    new ApiResponse(code = 401, message = "Unauthorized client ID."),
    new ApiResponse(code = 404, message = "No data was found for the list of query Ids."),
    new ApiResponse(code = 500, message = "Internal server error.")))
  def getSources( // scalastyle:ignore public.methods.have.type
    @ApiParam(value = "The MET API source ID(s) that you want metadata for. Enter a comma-separated list to select multiple sources.",
              required = false)
              ids: Option[String],
    @ApiParam(value = "The type of MET API source that you want metadata for.",
              required = false,
              allowableValues = "SensorSystem")
              types: Option[String],
    @ApiParam(value = "get only sources located within this WSEN bounding box",
              required = false)
              bbox: Option[String],
    @ApiParam(value = "The time during which the MET API source must be valid (i.e., operational).",
              required = false)
              validtime: Option[String],
    @ApiParam(value = "Fields to access",
              required = false,
              allowableValues = "value,unit,qualityCode")
              fields: Option[String],
    //@ApiParam(value = "limit the number of records returned",
    //          required = false,
    //          defaultValue = "100")
    //          limit: Option[Int],
    //@ApiParam(value = "returns from this offset in the result set",
    //          required = false)
    //          offset: Option[Int],
    //@ApiParam(value = "sets the namespace used in the query and the return set",
    //          required = false)
    //          namespace: Option[String],
    @ApiParam(value = "The output format of the result.",
              allowableValues = "jsonld",
              defaultValue = "jsonld",
              required = true)
              format: String) = no.met.security.AuthorizedAction {
    implicit request =>
    // Start the clock
    val start = DateTime.now(DateTimeZone.UTC)
    Try {
      val sourceList : Array[String] = ids match {
        case Some(id) => id.toUpperCase.split(",").map(_.trim)
        case _ => Array()
      }
      val bboxList : Array[Double] = bbox match {
        case Some(bbox) => bbox.split(",").map(_.toDouble) // TODO - check that exactly 0 or 4
        case _ => Array()
      }
      if (bboxList.length > 0 && bboxList.length != 4) throw new Exception("bbox parameter must contain exactly 4 comma-separated numbers")
      sourceAccess.getStations(sourceList, types, bboxList, validtime, fields)
    } match {
      case Success(data) =>
        if (data isEmpty) {
          NotFound("Found no data for sources " + ids.getOrElse("<all>"))
        } else {
          format.toLowerCase() match {
            case "jsonld" => Ok(JsonFormat.format(start, data)) as "application/vnd.no.met.data.sources-v0+json"
            case x        => BadRequest(s"Invalid output format: $x")
          }
        }
      case Failure(x) => BadRequest(x getLocalizedMessage)
    }
  }

}
