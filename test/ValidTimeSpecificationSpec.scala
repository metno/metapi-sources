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

import org.joda.time.DateTime
import services.sources._

import TestUtil._

@RunWith(classOf[JUnitRunner])
class ValidTimeSpecificationSpec extends Specification {

  "ValidTimeSpecification" should {

    "fail to parse invalid spec" in {
      ValidTimeSpecification("") must throwA[Exception]
      ValidTimeSpecification("/") must throwA[Exception]
      ValidTimeSpecification("foobar") must throwA[Exception]
      ValidTimeSpecification("/now") must throwA[Exception]
      ValidTimeSpecification("now/") must throwA[Exception]
      ValidTimeSpecification("2017") must throwA[Exception]
      ValidTimeSpecification("2017-03") must throwA[Exception]
      ValidTimeSpecification("2017-03-06T14") must throwA[Exception]
      ValidTimeSpecification("2017-03-06T14:52") must throwA[Exception]
      ValidTimeSpecification("2017-03-06T14:52:09") must throwA[Exception]
      ValidTimeSpecification("2017-03-06T14:52:09Z") must throwA[Exception]
      ValidTimeSpecification("now/2017-03-06") must throwA[Exception]
      ValidTimeSpecification("2017-03-06/ now") must throwA[Exception]
    }

    "parse single time (now)" in {
      val s = "now"
      ValidTimeSpecification(s).fromDateTime must beNone
      ValidTimeSpecification(s).toDateTime must beNone
    }

    "parse single time (now - case insensitive)" in {
      val s = "noW"
      ValidTimeSpecification(s).fromDateTime must beNone
      ValidTimeSpecification(s).toDateTime must beNone
    }

    "parse single time (explicit)" in {
      val s = "2017-03-06"
      ValidTimeSpecification(s).fromDateTime must beNone
      ValidTimeSpecification(s).toDateTime must beSome[DateTime]
    }

    "parse interval ([explicit, now])" in {
      val s = "2017-03-06/now"
      ValidTimeSpecification(s).fromDateTime must beSome[DateTime]
      ValidTimeSpecification(s).toDateTime must beNone
    }

    "parse interval ([explicit, explicit])" in {
      val t0 = "2017-03-06"
      val t1 = "2017-04-06"
      val s = s"$t0/$t1"
      ValidTimeSpecification(s).fromDateTime must beSome[DateTime]
      ValidTimeSpecification(s).toDateTime must beSome[DateTime]
    }

    "parse empty interval" in {
      val t0 = "2017-03-06"
      val s = s"$t0/$t0"
      ValidTimeSpecification(s).fromDateTime must beSome[DateTime]
      ValidTimeSpecification(s).toDateTime must beSome[DateTime]
    }

    "fail on negative interval" in {
      val t0 = "2017-03-06"
      val t1 = "2017-04-06"
      val s = s"$t1/$t0"
      ValidTimeSpecification(s).fromDateTime must throwA[Exception]
    }

  }

}
