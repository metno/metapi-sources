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
import TestUtil._
//import scala.concurrent.Future

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class SourcesApplicationSpec extends Specification {

  "sources plugin" should {

    "get api-docs" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/api-docs")).get
      status(response) must equalTo(OK)
      contentType(response) must beSome.which(_ == "application/json")
      contentAsString(response) must contain ("Describe sources of metapi data")
    }

    "get a list of stations" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?limit=10")).get
      contentAsString(response) must contain ("SensorSystem")
    }

    "get a specific station" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?sources=KN4200")).get
      val json = Json.parse(contentAsString(response))
      contentAsString(response) must contain ("KJELLER")
      (json \ "data").as[JsArray].value.size must equalTo(1)
    }

    "get a list of specific stations" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?sources=KN70740,KN377200,KN4794600")).get
      status(response) must equalTo(OK)
      val json = Json.parse(contentAsString(response))
      (json \ "data").as[JsArray].value.size must equalTo(3)
    }

    "find station by geographic coordinates" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?bbox=-30,58,0,66")).get
      status(response) must equalTo(OK)
      val json = Json.parse(contentAsString(response))
      contentAsString(response) must contain ("KEFLAVIKURFLUGVOLLUR")
      (json \ "data").as[JsArray].value.size must equalTo(1)
    }

    "returns correct contentType for getStations" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?limit=1")).get
      status(response) must equalTo(OK)
      contentType(response) must beSome.which(_ == "application/vnd.no.met.data.sources-v0+json")
    }

    "send 404 on a bad request" in new WithApplication(TestUtil.app) {
      route(FakeRequest(GET, "/boum")) must beSome.which (status(_) == NOT_FOUND)
    }

    "return 404 for incorrect id" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?sources=dummy")).get
      status(response) must equalTo(NOT_FOUND)
    }

    "return error for incorrect coordinates" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.jsonld?bbox=-30,58,0,66,99")).get
      status(response) must equalTo(BAD_REQUEST)
    }

    "returns error for incorrect format in getStations" in new WithApplication(TestUtil.app) {
      val response = route(FakeRequest(GET, "/v0.torrent?limit=1")).get
      status(response) must equalTo(BAD_REQUEST)
    }

  }

}
