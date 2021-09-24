package utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateUtils {

  val DATE_FORMAT = "yyyy-MM-dd"
  val FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT)
  val TIME_FORMAT = "yyyy-MM-dd HH:mm:ss"
  val TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_FORMAT)

  def parseDate(source: Option[String]): Option[LocalDate] =
    try source.map(s => LocalDate.parse(s, FORMATTER))
    catch {
      case _: DateTimeParseException => None
    }

  def formatTime(time: LocalDateTime) =
    time.format(TIME_FORMATTER)

  def formatTime(time: Option[LocalDateTime]) =
    time match {
      case None        => ""
      case Some(value) => value.format(TIME_FORMATTER)
    }

  def withDayOfWeek(date: LocalDate, day: DayOfWeek) = {
    var innerDate = date
    while (innerDate.getDayOfWeek != day)
      innerDate = innerDate.minusDays(1)
    innerDate
  }

}
