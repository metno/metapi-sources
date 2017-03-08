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

import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}
import org.joda.time.DateTime
import scala.util.{Try, Failure, Success}
import no.met.data._


// This class represents a time specification where the input may have one of the following forms:
//   1: <date>/<date>
//   2: <date>/now
//   3: <date>
//   4: now
// where <date> is of the form YYYY-MM-DD, e.g. 2017-03-06.
class ValidTimeSpecification(input: String) {

  private def extractParts(s: String): Map[String, Option[String]] = {
    val pattern = """(([^/]+)/([^/]+))|([^/]+)""".r
    s.trim match {
      case pattern(_, d0, d1, d) => Map("d0" -> Option(d0), "d1" -> Option(d1), "d" -> Option(d))
      case _ => throw new Exception("failed to extract substring(s) to parse")
    }
  }

  private def throwBadRequestException(reason: String) = {
    throw new BadRequestException(
      s"Failed to parse validTime: $reason",
      Some("Expected format: <date>/<date>, <date>/now, <date>, or now, where <date> is of the form YYYY-MM-DD, e.g. 2017-03-06")
    )
  }

  private val dtFormatter: DateTimeFormatter = ISODateTimeFormat.yearMonthDay

  private var fromDate: Option[DateTime] = None
  private var toDate: Option[DateTime] = None

  private def parseSingleDate(d: String) = {
    fromDate = None
    toDate = if (d.toLowerCase == "now") None else Try(dtFormatter.parseDateTime(d)) match {
      case Success(dt) => Some(dt)
      case Failure(e) => throwBadRequestException(e.getMessage)
    }
  }

  private def parseInterval(d0: String, d1: String) = {
    fromDate = Try(dtFormatter.parseDateTime(d0)) match {
      case Success(dt) => Some(dt)
      case Failure(e) => throwBadRequestException(e.getMessage)
    }
    toDate = if (d1.toLowerCase == "now") None else Try(dtFormatter.parseDateTime(d1)) match { // note that the 'to' date of the interval may be 'now'
      case Success(dt) => Some(dt)
      case Failure(e) => throwBadRequestException(e.getMessage)
    }

    if (toDate.nonEmpty && toDate.get.isBefore(fromDate.get)) {
      throwBadRequestException("negative interval")
    }
  }

  // Initializes object. Throws BadRequestException upon error.
  private def init() = {
    Try(extractParts(input)) match {
      case Success(m) => m("d") match {
        case Some(d) => {
          assert(m("d0").isEmpty && m("d1").isEmpty)
          parseSingleDate(d)
        }
        case None => { // an interval
          assert(m("d0").nonEmpty && m("d1").nonEmpty)
          parseInterval(m("d0").get, m("d1").get)
        }
      }
      case Failure(e) => throwBadRequestException(e.getMessage)
    }
  }

  // Returns Some(d0) if the time spec is an interval explicitly starting at d0: [d0, toDate]
  // Returns None if the time spec is a single date: toDate
  def fromDateTime: Option[DateTime] = fromDate

  // Returns Some(d1) for an explicit date d1, or None for the implicit current date: "now".
  def toDateTime: Option[DateTime] = toDate

  init() // initialize
}


/**
 * Parsing of valid time specification for sources.
 */
object ValidTimeSpecification {
  def apply(input: String): ValidTimeSpecification = new ValidTimeSpecification(input)
}
