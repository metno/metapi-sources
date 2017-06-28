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

package models

import io.swagger.annotations._
import scala.annotation.meta.field
import java.net.URL
import com.github.nscala_time.time.Imports._
import no.met.data.{ApiConstants,BasicResponse}
import no.met.geometry._

@ApiModel(description="Data response for source metadata.")
case class SourceResponse(
  @(ApiModelProperty @field)(name=ApiConstants.CONTEXT_NAME, value=ApiConstants.CONTEXT, example=ApiConstants.METAPI_CONTEXT) context: URL,
  @(ApiModelProperty @field)(name=ApiConstants.OBJECT_TYPE_NAME, value=ApiConstants.OBJECT_TYPE, example="SourceResponse") responseType: String,
  @(ApiModelProperty @field)(value=ApiConstants.API_VERSION, example=ApiConstants.API_VERSION_EXAMPLE) apiVersion: String,
  @(ApiModelProperty @field)(value=ApiConstants.LICENSE, example=ApiConstants.METAPI_LICENSE) license: URL,
  @(ApiModelProperty @field)(value=ApiConstants.CREATED_AT, dataType="String", example=ApiConstants.CREATED_AT_EXAMPLE) createdAt: DateTime,
  @(ApiModelProperty @field)(value=ApiConstants.QUERY_TIME, dataType="String", example=ApiConstants.QUERY_TIME_EXAMPLE) queryTime: Duration,
  @(ApiModelProperty @field)(value=ApiConstants.CURRENT_ITEM_COUNT, example=ApiConstants.CURRENT_ITEM_COUNT_EXAMPLE) currentItemCount: Long,
  @(ApiModelProperty @field)(value=ApiConstants.ITEMS_PER_PAGE, example=ApiConstants.ITEMS_PER_PAGE_EXAMPLE) itemsPerPage: Long,
  @(ApiModelProperty @field)(value=ApiConstants.OFFSET, example=ApiConstants.OFFSET_EXAMPLE) offset: Long,
  @(ApiModelProperty @field)(value=ApiConstants.TOTAL_ITEM_COUNT, example=ApiConstants.TOTAL_ITEM_COUNT_EXAMPLE) totalItemCount: Long,
  @(ApiModelProperty @field)(value=ApiConstants.NEXT_LINK, example=ApiConstants.NEXT_LINK_EXAMPLE) nextLink: Option[URL],
  @(ApiModelProperty @field)(value=ApiConstants.PREVIOUS_LINK, example=ApiConstants.PREVIOUS_LINK_EXAMPLE) previousLink: Option[URL],
  @(ApiModelProperty @field)(value=ApiConstants.CURRENT_LINK, example=ApiConstants.CURRENT_LINK_EXAMPLE) currentLink: URL,
  @(ApiModelProperty @field)(value=ApiConstants.DATA) data: List[Source]
)
extends BasicResponse( context, responseType, apiVersion, license, createdAt, queryTime, currentItemCount, itemsPerPage, offset, totalItemCount,
    nextLink, previousLink, currentLink)

@ApiModel(description="Metadata for a single source.")
case class Source(
  @(ApiModelProperty @field)(name="@type", value="The source type of the Source.", example="SensorSystem") sType: Option[String],
  @(ApiModelProperty @field)(value="The MET API id of the source.", example="SN18700") id: Option[String],
  @(ApiModelProperty @field)(value="The name of the source.", example="OSLO - BLINDERN") name: Option[String],
  @(ApiModelProperty @field)(value="The short name of the source.", example="Blindern") shortName: Option[String],
  @(ApiModelProperty @field)(value="The country affiliation of the source.", example="Norway") country: Option[String],
  @(ApiModelProperty @field)(value="The ISO 3166-1 alpha-2 code of the country.", example="NO") countryCode: Option[String],
  @(ApiModelProperty @field)(value="The assigned WMO number for a SensorSystem, if one exists.", example="1492") wmoId: Option[Int],
  @(ApiModelProperty @field)(value="Spatial location data for the source.") geometry: Option[Point],
  @(ApiModelProperty @field)(value="The elevation of the source in meters above sea level.", example="94") masl: Option[Double],
  @(ApiModelProperty @field)(value="The datetime from which the source is valid.", example="1974-05-29T00:00:00Z") validFrom: Option[String],
  @(ApiModelProperty @field)(value="The datetime to which the source was valid (if no longer valid).", example="2006-09-01T00:00:00Z") validTo: Option[String],
  @(ApiModelProperty @field)(value="County name.", example="Oppland") county: Option[String],
  @(ApiModelProperty @field)(value="County id.", example="5") countyId: Option[Int],
  @(ApiModelProperty @field)(value="Municipality name.", example="Lillehammer") municipality: Option[String],
  @(ApiModelProperty @field)(value="Municipality id.", example="501") municipalityId: Option[Int],
  @(ApiModelProperty @field)(value="Station holders.", example="[ \"MET.NO\", \"STATENS VEGVESEN\" ]") stationHolders: Option[Seq[String]],
  @(ApiModelProperty @field)(value="External ids.", example="[ \"01466\", \"10.249.0.126\", \"1466\", \"ENKJ\" ]") externalIds: Option[Seq[String]],
  @(ApiModelProperty @field)(value="ICAO codes.", example="[ \"ESNZ\", \"ESPC\" ]") icaoCodes: Option[Seq[String]],
  @(ApiModelProperty @field)(value="Ship codes.", example="[ \"JWBR\", \"LMXQ\" ]") shipCodes: Option[Seq[String]],
  @(ApiModelProperty @field)(value="WIGOS id.", example="0-578-0-18700") wigosId: Option[String]
)
