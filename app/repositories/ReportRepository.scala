package repositories

import java.time.{LocalDate, LocalDateTime, LocalTime, OffsetDateTime, YearMonth, ZoneOffset}
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models._
import play.api.Configuration
import repositories._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.{GetResult, JdbcProfile}
import utils.Constants.ActionEvent.MODIFICATION_COMMERCANT
import utils.Constants.{Departments, ReportStatus}
import utils.Constants.ReportStatus.ReportStatusValue
import utils.DateUtils
import utils.EmailAddress

import scala.concurrent.{ExecutionContext, Future}

case class ReportFilter(
                         departments: Seq[String] = List(),
                         email: Option[String] = None,
                         siret: Option[String] = None,
                         companyName: Option[String] = None,
                         start: Option[LocalDate] = None,
                         end: Option[LocalDate] = None,
                         category: Option[String] = None,
                         statusList: Seq[String] = List(),
                         details: Option[String] = None,
                         employeeConsumer: Option[Boolean] = None
                       )

@Singleton
class ReportRepository @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                 companyAccessRepository: CompanyAccessRepository,
                                 val companyRepository: CompanyRepository,
                                 configuration: Configuration)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._
  import models.DetailInputValue._

  class ReportTable(tag: Tag) extends Table[Report](tag, "reports") {

    def id = column[UUID]("id", O.PrimaryKey)
    def category = column[String]("category")
    def subcategories = column[List[String]]("subcategories")
    def details = column[List[String]]("details")
    def companyId = column[Option[UUID]]("company_id")
    def companyName = column[String]("company_name")
    def companyAddress = column[String]("company_address")
    def companyPostalCode = column[Option[String]]("company_postal_code")
    def companySiret = column[Option[String]]("company_siret")
    def creationDate= column[OffsetDateTime]("creation_date")
    def firstName = column[String]("first_name")
    def lastName = column[String]("last_name")
    def email = column[EmailAddress]("email")
    def contactAgreement = column[Boolean]("contact_agreement")
    def employeeConsumer = column[Boolean]("employee_consumer")
    def status = column[Option[String]]("status")

    def company = foreignKey("COMPANY_FK", companyId, companyRepository.companyTableQuery)(_.id.?, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)

    type ReportData = (UUID, String, List[String], List[String], Option[UUID], String, String, Option[String], Option[String], OffsetDateTime, String, String, EmailAddress, Boolean, Boolean, Option[String])

    def constructReport: ReportData => Report = {
      case (id, category, subcategories, details, companyId, companyName, companyAddress, companyPostalCode, companySiret, creationDate, firstName, lastName, email, contactAgreement, employeeConsumer, status) =>
        Report(Some(id), category, subcategories, details.filter(_ != null).map(string2detailInputValue(_)), companyId, companyName, companyAddress, companyPostalCode, companySiret,
          Some(creationDate), firstName, lastName, email, contactAgreement, employeeConsumer, List.empty, status.map(ReportStatus.fromDefaultValue(_)))
    }

    def extractReport: PartialFunction[Report, ReportData] = {
      case Report(id, category, subcategories, details, companyId, companyName, companyAddress, companyPostalCode, companySiret,
      creationDate, firstName, lastName, email, contactAgreement, employeeConsumer, files, status) =>
        (id.get, category, subcategories, details.map(detailInputValue => s"${detailInputValue.label} ${detailInputValue.value}"), companyId, companyName, companyAddress, companyPostalCode, companySiret,
          creationDate.get, firstName, lastName, email, contactAgreement, employeeConsumer, status.map(_.defaultValue))
    }

    def * =
      (id, category, subcategories, details, companyId, companyName, companyAddress, companyPostalCode, companySiret,
        creationDate, firstName, lastName, email, contactAgreement, employeeConsumer, status) <> (constructReport, extractReport.lift)
  }

  implicit val ReportFileOriginColumnType = MappedColumnType.base[ReportFileOrigin, String](_.value, ReportFileOrigin(_))

  private class FileTable(tag: Tag) extends Table[ReportFile](tag, "report_files") {

    def id = column[UUID]("id", O.PrimaryKey)
    def reportId = column[Option[UUID]]("report_id")
    def creationDate = column[OffsetDateTime]("creation_date")
    def filename = column[String]("filename")
    def origin = column[ReportFileOrigin]("origin")
    def report = foreignKey("report_files_fk", reportId, reportTableQuery)(_.id.?)

    type FileData = (UUID, Option[UUID], OffsetDateTime, String, ReportFileOrigin)

    def constructFile: FileData => ReportFile = {
      case (id, reportId, creationDate, filename, origin) => ReportFile(id, reportId, creationDate, filename, origin)
    }

    def extractFile: PartialFunction[ReportFile, FileData] = {
      case ReportFile(id, reportId, creationDate, filename, origin) => (id, reportId, creationDate, filename, origin)
    }

    def * =
      (id, reportId, creationDate, filename, origin) <> (constructFile, extractFile.lift)
  }

  val reportTableQuery = TableQuery[ReportTable]

  private val fileTableQuery = TableQuery[FileTable]

  private val companyTableQuery = companyRepository.companyTableQuery

  private val date = SimpleFunction.unary[OffsetDateTime, LocalDate]("date")

  private val date_part = SimpleFunction.binary[String, OffsetDateTime, Int]("date_part")

  private val array_to_string = SimpleFunction.ternary[List[String], String, String, String]("array_to_string")

  val backofficeAdminStartDate = OffsetDateTime.of(
    LocalDate.parse(configuration.get[String]("play.stats.backofficeAdminStartDate")),
    LocalTime.MIDNIGHT,
    ZoneOffset.UTC)

  implicit class RegexLikeOps(s: Rep[String]) {
    def regexLike(p: Rep[String]): Rep[Boolean] = {
      val expr = SimpleExpression.binary[String,String,Boolean] { (s, p, qb) =>
        qb.expr(s)
        qb.sqlBuilder += " ~* "
        qb.expr(p)
      }
      expr.apply(s,p)
    }
  }

  def create(report: Report): Future[Report] = db
    .run(reportTableQuery += report)
    .map(_ => report)

  def list: Future[List[Report]] = db.run(reportTableQuery.to[List].result)

  def update(report: Report): Future[Report] = {
    val queryReport = for (refReport <- reportTableQuery if refReport.id === report.id)
      yield refReport
    db.run(queryReport.update(report))
      .map(_ => report)
  }

  def count(siret: Option[String] = None): Future[Int] = db
    .run(reportTableQuery
      .filterOpt(siret) {
        case(table, siret) => table.companySiret === siret
      }
      .length.result)

  def monthlyCount: Future[List[MonthlyStat]] = db
    .run(
      reportTableQuery
        .filter(report => report.creationDate > OffsetDateTime.now().minusYears(1))
        .groupBy(report => (date_part("month", report.creationDate), date_part("year", report.creationDate)))
        .map{
          case ((month, year), group) => (month, year, group.length)
        }
        .to[List].result
    )
    .map(_.map(result => MonthlyStat(result._3, YearMonth.of(result._2, result._1))))

  val baseStatReportTableQuery = reportTableQuery
    .filter(_.companyPostalCode.map(_.substring(0, 2) inSet Departments.AUTHORIZED).getOrElse(false))
    .filter(_.creationDate > backofficeAdminStartDate)
  val baseMonthlyStatReportTableQuery = baseStatReportTableQuery.filter(report => report.creationDate > OffsetDateTime.now().minusYears(1))

  def countWithStatus(statusList: List[ReportStatusValue]) = db
    .run(
      baseStatReportTableQuery
        .filter(_.status inSet statusList.map(_.defaultValue))
        .length
        .result
    )

  def countMonthlyWithStatus(statusList: List[ReportStatusValue]): Future[List[MonthlyStat]] = db
    .run(
      baseMonthlyStatReportTableQuery
        .filter(_.status inSet statusList.map(_.defaultValue))
        .groupBy(report => (date_part("month", report.creationDate), date_part("year", report.creationDate)))
        .map{
          case ((month, year), group) => (month, year, group.length)
        }
        .to[List].result
    )
    .map(_.map(result => MonthlyStat(result._3, YearMonth.of(result._2, result._1))))


  def getReport(id: UUID): Future[Option[Report]] = db.run {
    reportTableQuery
      .filter(_.id === id)
      .joinLeft(fileTableQuery).on(_.id === _.reportId)
      .to[List]
      .result
      .map(result =>
        result.map(_._1).distinct
          .map(report => report.copy(files = result.map(_._2).distinct.filter(_.map(_.reportId == report.id).getOrElse(false)).map(_.get)))
          .headOption
      )
  }

  def delete(id: UUID): Future[Int] = db.run {
    reportTableQuery
      .filter(_.id === id)
      .delete
  }

  def getReports(offset: Long, limit: Int, filter: ReportFilter): Future[PaginatedResult[Report]] = db.run {

      val query = reportTableQuery
          .filterIf(filter.departments.length > 0) {
            case table => table.companyPostalCode.map(cp => cp.substring(0, 2).inSet(filter.departments)).getOrElse(false)
          }
          .filterOpt(filter.email) {
            case(table, email) => table.email === EmailAddress(email)
          }
          .filterOpt(filter.siret) {
            case(table, siret) => table.companySiret === siret
          }
          .filterOpt(filter.companyName) {
            case(table, companyName) => table.companyName like s"${companyName}%"
          }
          .filterOpt(filter.start) {
            case(table, start) => date(table.creationDate) >= start
          }
          .filterOpt(filter.end) {
            case(table, end) => date(table.creationDate) < end
          }
          .filterOpt(filter.category) {
            case(table, category) => table.category === category
          }
          .filterIf(filter.statusList.length > 0) {
            case table => table.status.inSet(filter.statusList).getOrElse(false)
          }
          .filterOpt(filter.details) {
            case(table, details) => array_to_string(table.subcategories, ",", "") ++ array_to_string(table.details, ",", "") regexLike s"${details}"
          }
          .filterOpt(filter.employeeConsumer) {
            case(table, employeeConsumer) => table.employeeConsumer === employeeConsumer
          }


    for {
        reports <- query
          .sortBy(_.creationDate.desc)
          .drop(offset)
          .take(limit)
          .joinLeft(fileTableQuery).on(_.id === _.reportId)
          .sortBy(_._1.creationDate.desc)
          .to[List]
          .result
          .map(result =>
            result.map(_._1).distinct
              .map(report => report.copy(files = result.map(_._2).distinct.filter(_.map(_.reportId == report.id).getOrElse(false)).map(_.get)))
          )
        count <- query.length.result
      } yield PaginatedResult(
        totalCount = count,
        entities = reports,
        hasNextPage = count - ( offset + limit ) > 0
      )
  }

  def createFile(file: ReportFile): Future[ReportFile] = db
    .run(fileTableQuery += file)
    .map(_ => file)

  def attachFilesToReport(fileIds: List[UUID], reportId: UUID) = {
    val queryFile = for (refFile <- fileTableQuery.filter(_.id.inSet(fileIds)))
      yield refFile.reportId
    db.run(queryFile.update(Some(reportId)))
  }

  def getFile(uuid: UUID): Future[Option[ReportFile]] = db
    .run(
      fileTableQuery
        .filter(_.id === uuid)
        .to[List].result
        .headOption
    )

  def retrieveReportFiles(reportId: UUID): Future[List[ReportFile]] = db
    .run(
      fileTableQuery
        .filter(_.reportId === reportId)
        .to[List].result
    )

  def deleteFile(uuid: UUID): Future[Int] = db
    .run(
      fileTableQuery
        .filter(_.id === uuid)
        .delete
    )

  def getNbReportsGroupByCompany(offset: Long, limit: Int): Future[PaginatedResult[CompanyWithNbReports]] = {

    implicit val getCompanyWithNbReports = GetResult(r => CompanyWithNbReports(r.nextString, r.nextString, r.nextString, r.nextString, r.nextInt))

    for {
      res <- db.run(
      sql"""select company_siret, company_postal_code, company_name, company_address, count(*)
        from reports
        group by company_siret, company_postal_code, company_name, company_address
        order by count(*) desc
        """.as[(CompanyWithNbReports)]
      )
    } yield {

      PaginatedResult(
        totalCount = res.length,
        entities = res.drop(offset.toInt).take(limit).toList,
        hasNextPage = res.length - ( offset + limit ) > 0
      )
    }
  }

  def getReportsForStatusWithAdmins(status: ReportStatusValue): Future[List[(Report, List[User])]] = {
    for {
      reports <- db.run {
                  reportTableQuery
                    .filter(_.status === status.defaultValue)
                    .to[List]
                    .result
                }
      adminsMap <- companyAccessRepository.fetchAdminsByCompany(reports.flatMap(_.companyId))
    } yield reports.flatMap(r => r.companyId.map(companyId => (r, adminsMap.getOrElse(companyId, Nil))))
  }
}
