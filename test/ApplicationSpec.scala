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

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import play.api.test.Helpers._
import play.api.libs.json._
import no.met.data._

import TestUtil._

@RunWith(classOf[JUnitRunner])
class SourcesApplicationSpec extends Specification {

  "sources plugin" should {

    "get a list of stations" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld")).get
      contentAsString(response) must contain (StationConfig.typeName)
      contentAsString(response) must contain (IDFGridConfig.typeName)
    }

    "get a specific station" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?ids=SN4200")).get
      val json = Json.parse(contentAsString(response))
      contentAsString(response) must contain ("KJELLER")
      (json \ "data").as[JsArray].value.size must equalTo(1)
    }

    "get a list of specific stations" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?ids=SN70740,SN377200,SN4794600")).get
      status(response) must equalTo(OK)
      val json = Json.parse(contentAsString(response))
      (json \ "data").as[JsArray].value.size must equalTo(3)
    }

    "return correct contentType" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld")).get
      status(response) must equalTo(OK)
      contentType(response) must beSome.which(_ == "application/vnd.no.met.data.sources-v0+json")
    }

    "send 404 on a bad request" in new WithApplication(TestUtil.app) {
      route(FakeRequest(GET, "/boum")) must beSome.which (status(_) == NOT_FOUND)
    }

    "return 400 for incorrect source id" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?ids=dummy")).get
      status(response) must equalTo(BAD_REQUEST)
    }

    "return error for incorrect format in getStations" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.torrent?limit=1")).get
      status(response) must equalTo(BAD_REQUEST)
    }

    "get a list of stations with fields specified" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?fields=id,country")).get
      status(response) must equalTo(OK)
      contentAsString(response) must contain (StationConfig.typeName)
    }

    "allow matching type 1 (1)" in new WithApplication(TestUtil.app) {
      val id = "4200"
      val response = route(FakeRequest(GET, s"/v0.jsonld?ids=SN${id}&types=SensorSystem")).get
      status(response) must equalTo(OK)
      contentAsString(response) must contain (s"${'"'}${id}${'"'}")
    }

    "allow matching type 1 (2)" in new WithApplication(TestUtil.app) {
      val id = "4200"
      val response = route(FakeRequest(GET, s"/v0.jsonld?ids=SN${id}&types=SensorSystem,InterpolatedDataset")).get
      status(response) must equalTo(OK)
      contentAsString(response) must contain (s"${'"'}${id}${'"'}")
    }

    "allow matching type 2 (1)" in new WithApplication(TestUtil.app) {
      val id = IDFGridConfig.name
      val response = route(FakeRequest(GET, s"/v0.jsonld?ids=${id}&types=InterpolatedDataset")).get
      status(response) must equalTo(OK)
      contentAsString(response) must contain (s"${'"'}${id}${'"'}")
    }

    "allow matching type 2 (2)" in new WithApplication(TestUtil.app) {
      val id = IDFGridConfig.name
      val response = route(FakeRequest(GET, s"/v0.jsonld?ids=${id}&types=SensorSystem,InterpolatedDataset")).get
      status(response) must equalTo(OK)
      contentAsString(response) must contain (s"${'"'}${id}${'"'}")
    }

    "fail on specifying unsupported type" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, s"/v0.jsonld?types=foobar")).get
      status(response) must equalTo(BAD_REQUEST)
    }

    "fail on specifying unsupported type 1" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, s"/v0.jsonld?ids=SN4200&types=SensorSystem,foobar")).get
      status(response) must equalTo(BAD_REQUEST)
    }

    "fail on specifying unsupported type 2" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, s"/v0.jsonld?ids=${IDFGridConfig.name}&types=InterpolatedDataset,foobar")).get
      status(response) must equalTo(BAD_REQUEST)
    }

    "fail on requesting wrong type 1" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, s"/v0.jsonld?ids=${IDFGridConfig.name}&types=SensorSystem")).get
      status(response) must equalTo(BAD_REQUEST)
    }

    "fail on requesting wrong type 2" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, s"/v0.jsonld?ids=SN1234&types=InterpolatedDataset")).get
      status(response) must equalTo(BAD_REQUEST)
    }

    "return only specific type 1" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?types=SensorSystem")).get
      contentAsString(response) must contain (StationConfig.typeName)
      contentAsString(response) must not contain IDFGridConfig.typeName
    }

    "return only specific type 2" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?types=InterpolatedDataset")).get
      contentAsString(response) must not contain StationConfig.typeName
      contentAsString(response) must contain (IDFGridConfig.typeName)
    }

  }

}
