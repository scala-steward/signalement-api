package repositories

import java.time.{LocalDate, LocalDateTime, OffsetDateTime, YearMonth}
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models._
import repositories._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.{GetResult, JdbcProfile}
import utils.Constants.ActionEvent.MODIFICATION_COMMERCANT
import utils.Constants.ReportStatus
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
                         details: Option[String] = None
                       )

@Singleton
class ReportRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, userRepository: UserRepository, val companyRepository: CompanyRepository)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._
  import models.DetailInputValue._

  class ReportTable(tag: Tag) extends Table[Report](tag, "signalement") {

    def id = column[UUID]("id", O.PrimaryKey)
    def category = column[String]("categorie")
    def subcategories = column[List[String]]("sous_categories")
    def details = column[List[String]]("details")
    def companyId = column[Option[UUID]]("company_id")
    def companyName = column[String]("nom_etablissement")
    def companyAddress = column[String]("adresse_etablissement")
    def companyPostalCode = column[Option[String]]("code_postal")
    def companySiret = column[Option[String]]("siret_etablissement")
    def creationDate= column[OffsetDateTime]("date_creation")
    def firstName = column[String]("prenom")
    def lastName = column[String]("nom")
    def email = column[EmailAddress]("email")
    def contactAgreement = column[Boolean]("accord_contact")
    def status = column[Option[String]]("status")

    def company = foreignKey("COMPANY_FK", companyId, companyRepository.companyTableQuery)(_.id.?, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)

    type ReportData = (UUID, String, List[String], List[String], Option[UUID], String, String, Option[String], Option[String], OffsetDateTime, String, String, EmailAddress, Boolean, Option[String])

    def constructReport: ReportData => Report = {
      case (id, category, subcategories, details, companyId, companyName, companyAddress, companyPostalCode, companySiret, creationDate, firstName, lastName, email, contactAgreement, status) =>
        Report(Some(id), category, subcategories, details.filter(_ != null).map(string2detailInputValue(_)), companyId, companyName, companyAddress, companyPostalCode, companySiret,
          Some(creationDate), firstName, lastName, email, contactAgreement, List.empty, status.map(ReportStatus.fromDefaultValue(_)))
    }

    def extractReport: PartialFunction[Report, ReportData] = {
      case Report(id, category, subcategories, details, companyId, companyName, companyAddress, companyPostalCode, companySiret,
      creationDate, firstName, lastName, email, contactAgreement, files, status) =>
        (id.get, category, subcategories, details.map(detailInputValue => s"${detailInputValue.label} ${detailInputValue.value}"), companyId, companyName, companyAddress, companyPostalCode, companySiret,
          creationDate.get, firstName, lastName, email, contactAgreement, status.map(_.defaultValue))
    }

    def * =
      (id, category, subcategories, details, companyId, companyName, companyAddress, companyPostalCode, companySiret,
        creationDate, firstName, lastName, email, contactAgreement, status) <> (constructReport, extractReport.lift)
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

  private val reportTableQuery = TableQuery[ReportTable]

  private val fileTableQuery = TableQuery[FileTable]

  private val userTableQuery = TableQuery[userRepository.UserTable]

  private val date = SimpleFunction.unary[OffsetDateTime, LocalDate]("date")

  private val date_part = SimpleFunction.binary[String, OffsetDateTime, Int]("date_part")

  private val array_to_string = SimpleFunction.ternary[List[String], String, String, String]("array_to_string")

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

  def avgDurationsForSendingReport() = {

    // date de mise en place du back office. Nécessaire de filtrer sur cette date pour avoir des chiffres cohérents
    val ORIGIN_BO = "2019-05-20"

    db.run(
      sql"""select EXTRACT(DAY from AVG(AGE(e1.creation_date, signalement.date_creation)))
           from events e1
           inner join signalement on e1.report_id = signalement.id
           where action = 'Envoi du signalement'
           and not exists(select * from events e2 where e2.report_id = e1.report_id and e2.action = 'Envoi du signalement' and e2.creation_date < e1.creation_date)
           and date_creation > to_date($ORIGIN_BO, 'yyyy-mm-dd')
         """.as[Int].headOption
    )
  }

  def protectString(str: String) = {
    str.replace("'", "''")
  }

  def nbSignalementsBetweenDates(start: String = DateUtils.formatTime(DateUtils.getOriginDate()), end: String = DateUtils.formatTime(LocalDateTime.now), departments: Option[List[String]] = None, statusList: Option[List[ReportStatusValue]] = None, withoutSiret: Boolean = false) = {

    val whereDepartments = departments match {
      case None => ""
      case Some(list) => " and (" + list.map(dep => s"code_postal like '$dep%'").mkString(" or ") + ")"
    }

    val whereStatus = statusList match {
      case None => ""
      case Some(list) => " and (" + list.map(status => s"status = '${protectString(status.defaultValue)}'").mkString(" or ") + ")"
    }

    val whereSiret = withoutSiret match {
      case true => s" and siret_etablissement is null or (siret_etablissement is not null and events.action = '${MODIFICATION_COMMERCANT.value}')"
      case false => ""
    }

    db.run(
      sql"""select count(distinct signalement.id)
         from signalement
         left join events on signalement.id = events.report_id
         where 1 = 1
         and date_creation > to_timestamp($start, 'yyyy-mm-dd hh24:mi:ss')
         and date_creation < to_timestamp($end, 'yyyy-mm-dd hh24:mi:ss')
         #$whereDepartments
         #$whereStatus
         #$whereSiret
      """.as[Int].headOption
    )
  }

  def nbSignalementsByCategory(start: String = DateUtils.formatTime(DateUtils.getOriginDate()), end: String = DateUtils.formatTime(LocalDateTime.now), departments: Option[List[String]] = None): Future[Vector[ReportsByCategory]] = {

    val whereDepartments = departments match {
      case None => ""
      case Some(seq) => " and (" + seq.map(dep => s"code_postal like '$dep%'").mkString(" or ") + ")"
    }

    implicit val getReportByCategoryResult = GetResult(r => ReportsByCategory(r.nextString, r.nextInt))

    db.run(
      sql"""select categorie, count(distinct signalement.id)
         from signalement
         left join events on signalement.id = events.report_id
         where 1 = 1
         and date_creation > to_timestamp($start, 'yyyy-mm-dd hh24:mi:ss')
         and date_creation < to_timestamp($end, 'yyyy-mm-dd hh24:mi:ss')
         #$whereDepartments
         group by categorie
      """.as[(ReportsByCategory)]
    )
  }


  def countPerMonth: Future[List[ReportsPerMonth]] = db
    .run(
      reportTableQuery
        .groupBy(report => (date_part("month", report.creationDate), date_part("year", report.creationDate)))
        .map{
          case ((month, year), group) => (month, year, group.length)
        }
        .to[List].result
    )
    .map(_.map(result => ReportsPerMonth(result._3, YearMonth.of(result._2, result._1))))

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
      sql"""select siret_etablissement, code_postal, nom_etablissement, adresse_etablissement, count(*)
        from signalement
        group by siret_etablissement, code_postal, nom_etablissement, adresse_etablissement
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

  def getReportsForStatusWithUser(status: ReportStatusValue): Future[List[(Report, User)]] = db.run {
    reportTableQuery
      .filter(_.status === status.defaultValue)
      .join(userTableQuery).on(_.companySiret === _.login)
      .to[List]
      .result
  }




}

