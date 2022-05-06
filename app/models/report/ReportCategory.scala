package models.report

import controllers.error.AppError.MalformedBody
import play.api.libs.json.JsResult
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.libs.json.Writes

sealed case class ReportCategory(value: String)

object ReportCategory {
  val Covid = ReportCategory("COVID-19 (coronavirus)")
  val CafeRestaurant = ReportCategory("Café / Restaurant")
  val AchatMagasin = ReportCategory("Achat / Magasin")
  val Service = ReportCategory("Services aux particuliers")
  val TelEauGazElec = ReportCategory("Téléphonie / Eau-Gaz-Electricité")
  val EauGazElec = ReportCategory("Eau / Gaz / Electricité")
  val TelFaiMedias = ReportCategory("Téléphonie / Fournisseur d'accès internet / médias")
  val BanqueAssuranceMutuelle = ReportCategory("Banque / Assurance / Mutuelle")
  val ProduitsObjets = ReportCategory("Produits / Objets")
  val Internet = ReportCategory("Internet (hors achats)")
  val TravauxRenovations = ReportCategory("Travaux / Rénovation")
  val VoyageLoisirs = ReportCategory("Voyage / Loisirs")
  val Immobilier = ReportCategory("Immobilier")
  val Sante = ReportCategory("Secteur de la santé")
  val VoitureVehicule = ReportCategory("Voiture / Véhicule")
  val Animaux = ReportCategory("Animaux")
  val DemarchesAdministratives = ReportCategory("Démarches administratives")
  val AchatMagasinInternet = ReportCategory("Achat (Magasin ou Internet)")
  val IntoxicationAlimentaire = ReportCategory("Intoxication alimentaire")
  val VoitureVehiculeVelo = ReportCategory("Voiture / Véhicule / Vélo")
  val DemarchageAbusif = ReportCategory("Démarchage abusif")
  val RetraitRappelSpecifique = ReportCategory("Retrait-Rappel pécifique")

  def fromValue(v: String) =
    List(
      CafeRestaurant,
      AchatMagasin,
      Covid,
      Service,
      TelEauGazElec,
      EauGazElec,
      TelFaiMedias,
      BanqueAssuranceMutuelle,
      ProduitsObjets,
      Internet,
      TravauxRenovations,
      VoyageLoisirs,
      Immobilier,
      Sante,
      VoitureVehicule,
      Animaux,
      DemarchesAdministratives,
      AchatMagasinInternet,
      IntoxicationAlimentaire,
      VoitureVehiculeVelo,
      DemarchageAbusif,
      RetraitRappelSpecifique
    ).find(_.value == v).fold(throw MalformedBody)(identity)

  implicit val reads = new Reads[ReportCategory] {
    def reads(json: JsValue): JsResult[ReportCategory] = json.validate[String].map(fromValue(_))
  }
  implicit val writes = new Writes[ReportCategory] {
    def writes(kind: ReportCategory) = Json.toJson(kind.value)
  }
}
