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
import TestUtil._

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
@RunWith(classOf[JUnitRunner])
class SourcesIntegrationSpec extends Specification {

  // No idea how to integrate this with Testutil... FIXME

  //"sources plugin" should {

    //"get a list of stations" in new WithBrowser {
      //browser.goTo("http://localhost:" + port + "/v0.jsonld?limit=10")
      //browser.pageSource must contain("SensorSystem")
    //}

    //"get a specific station" in new WithBrowser {
      //browser.goTo("http://localhost:" + port + "/v0.jsonld?sources=KN4200")
      //browser.pageSource must contain("KJELLER")
    //}

  //}

}
