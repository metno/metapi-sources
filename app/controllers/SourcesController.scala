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
import no.met.data._
import models.Source
import services.sources.{ SourceAccess, JsonFormat }

// scalastyle:off magic.number
// scalastyle:off line.size.limit

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
              allowableValues = "SensorSystem,InterpolatedDataset")
              types: Option[String],
    @ApiParam(value = "Get MET API sources defined by a specified geometry. Geometries are specified as either a POINT or POLYGON using <a href='https://en.wikipedia.org/wiki/Well-known_text'>WKT</a>; see the reference section on the <a href=concepts/index.html#geometry_specification>Geometry Specification</a> for documentation and examples.",
              required = false)
              geometry: Option[String],
    @ApiParam(value = "If specified, only sources that have been, or still are, valid/applicable during some part of this interval may be included in the result. Specify &lt;date&gt;/&lt;date&gt;, &lt;date&gt;/now, &lt;date&gt;, or now, where &lt;date&gt; is of the form YYYY-MM-DD, e.g. 2017-03-06. The default is 'now', i.e. only currently valid/applicable sources are included.",
              required = false)
              validtime: Option[String],
    @ApiParam(value = "If specified, only sources whose 'name' attribute matches this filter may be included in the result. Optional wildcard asterisks may be specified (e.g. 'li\\*eha\\*' would match 'Lillehammer').",
              required = false)
              name: Option[String],
    @ApiParam(value = "If specified, only sources whose 'country' or 'countryCode' attribute matches this filter may be included in the result. Optional wildcard asterisks may be specified (e.g. '\\*in\\*d' would match 'Finland').",
              required = false)
              country: Option[String],
    @ApiParam(value = "If specified, only sources whose 'stationHolders' attribute contains at least one name that matches this filter may be included in the result. Optional wildcard asterisks may be specified (e.g. '\\*atens\\*vegves\\*' would match 'STATENS VEGVESEN').",
              required = false)
              stationholder: Option[String],
    @ApiParam(value = "If specified, only sources whose 'externalIds' attribute contains at least one name that matches this filter may be included in the result. Optional wildcard asterisks may be specified (e.g. '\\*466' would match '01466').",
              required = false)
              externalid: Option[String],
    @ApiParam(value = "A comma-separated list of the fields that should be present in the response. If set, only those properties listed here will be visible in the result set; e.g.: id,country will show only those two entries in the data set.",
              required = false)
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
    val start = DateTime.now(DateTimeZone.UTC) // start the clock
    Try {
      // ensure that the query string contains supported fields only
      QueryStringUtil.ensureSubset(
        Set("ids", "types", "geometry", "validtime", "name", "country", "stationholder", "externalid", "fields"), request.queryString.keySet)

      val srcSpec = SourceSpecification(ids, types)
      val fieldList : Set[String] = FieldSpecification.parse(fields)

      sourceAccess.getSources(srcSpec, geometry, validtime, name, country, stationholder, externalid, fieldList)

    } match {
      case Success(data) =>
        if (data isEmpty) {
          Error.error(NOT_FOUND, Some("No data found for this combination of query parameters"), None, start)
        } else {
          format.toLowerCase() match {
            case "jsonld" => Ok(JsonFormat.format(start, data)) as "application/vnd.no.met.data.sources-v0+json"
            case x        => Error.error(BAD_REQUEST, Some(s"Invalid output format: $x"), Some("Supported output formats: jsonld"), start)
          }
        }
      case Failure(x: BadRequestException) =>
        Error.error(BAD_REQUEST, Some(x getLocalizedMessage), x help, start)
      case Failure(x) => {
        //$COVERAGE-OFF$
        Logger.error(x.getLocalizedMessage)
        Error.error(INTERNAL_SERVER_ERROR, Some("An internal error occurred"), None, start)
        //$COVERAGE-ON$
      }
    }
  }

}

// scalastyle:on
