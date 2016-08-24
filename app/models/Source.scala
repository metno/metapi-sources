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
import no.met.geometry.Point

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
  @(ApiModelProperty @field)(value=ApiConstants.DATA) data: Seq[Source]
) 
extends BasicResponse( context, responseType, apiVersion, license, createdAt, queryTime, currentItemCount, itemsPerPage, offset, totalItemCount, nextLink, previousLink, currentLink)

@ApiModel(description="Metadata for a single source.")
case class Source(
  @(ApiModelProperty @field)(name="@type", value="The source type of the Source.", example="SensorSystem") sType: String,
  @(ApiModelProperty @field)(value="The MET API id of the source.", example="SN18700") id: String,
  @(ApiModelProperty @field)(value="The name of the source.", example="OSLO - BLINDERN") name: String,
  @(ApiModelProperty @field)(value="The country affiliation of the source.", example="Norway.") country: String,
  @(ApiModelProperty @field)(value="The assigned WMO number for a SensorSystem, if one exists.", example="1492") wmoNumber: Option[Int],
  @(ApiModelProperty @field)(value="Spatial location data for the source.") geometry: Option[Point],
  @(ApiModelProperty @field)(value="The level of the source.") level: Option[Double],
  @(ApiModelProperty @field)(value="The level unit of the source.") levelUnit: Option[String],
  @(ApiModelProperty @field)(value="The level typeof the source.") levelType: Option[String],
  @(ApiModelProperty @field)(value="The date from which the source was valid.") validFrom: String,
  @(ApiModelProperty @field)(value="The date to which the source was valid (if no longer valid).") validTo: Option[String]
)
