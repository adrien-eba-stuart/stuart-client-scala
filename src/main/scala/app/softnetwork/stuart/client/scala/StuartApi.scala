package app.softnetwork.stuart.client.scala

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods

import akka.stream.Materializer
import app.softnetwork.api.client.auth.{Oauth2ApiConfig, Oauth2Authenticator}
import org.apache.commons.lang3.StringUtils

import org.json4s.Formats

import app.softnetwork.api.client.GenericApi

import app.softnetwork.stuart.client.scala.message._
import app.softnetwork.stuart.client.scala.model._
import app.softnetwork.stuart.client.scala.serialization._

import scala.language.implicitConversions

/**
  * Created by smanciot on 31/03/2021.
  */
sealed trait StuartApi extends GenericApi with Oauth2Authenticator with StuartAddressApi with StuartJobApi{
  override implicit def formats: Formats = stuartFormats
  override lazy val config = Settings.StuartConfig
}

trait StuartAddressApi {_: StuartApi =>

  def validateAddress(address: String, picking: Boolean = true): Either[StuartError, AddressValidated] = {
    val `type` =
      if(picking){
        "picking"
      }
      else {
        "delivering"
      }
    doGet[AddressValidated, StuartError](
      "/v2/addresses/validate",
      Map("address" -> address, "type" -> `type`)
    )
  }

  def listZonesPerCountry(country: String): Seq[String] = {
    config.zones(country)
  }

  def checkZone(country: String = "france", town: String): Boolean = {
    config.zones(country).contains(StringUtils.stripAccents(town).replace(' ', '_').replace('-', '_').toLowerCase())
  }
}

trait StuartJobApi {_: StuartApi =>

  def calculatePricing(job: JobRequest): Either[StuartError, PricingCalculated] = {
    doPost[CalculatePricing, PricingCalculated, StuartError](
      "/v2/jobs/pricing",
      CalculatePricing(job)
    )
  }

  def validateJob(job: JobRequest): Either[StuartError, JobValidated] = {
    doPost[ValidateJob, JobValidated, StuartError](
      "/v2/jobs/validate",
      ValidateJob(job)
    )
  }

  def eta(job: JobRequest): Either[StuartError, JobEta] = {
    doPost[RequestJobEta, JobEta, StuartError](
      "/v2/jobs/eta",
      RequestJobEta(job)
    )
  }

  def createJob(job: JobRequest): Either[StuartError, Job] = {
    doPost[CreateJob, Job, StuartError](
      "/v2/jobs",
      CreateJob(job)
    )
  }

  def updateJob(job_id: Int, job: JobPatch): Either[StuartError, Unit] = {
    executeWithoutResponse[UpdateJob, StuartError](
      s"/v2/jobs/$job_id",
      UpdateJob(job),
      HttpMethods.PATCH
    )
  }

  def listJobs(jobQuery: JobQuery): Either[StuartError, Seq[Job]] = {
    val query = jobQuery2Map(jobQuery)
    logger.debug(s"query -> $query")
    doGet[Seq[Job], StuartError]("/v2/jobs", query)
  }

  def getJob(job_id: Int): Either[StuartError, Job] = {
    doGet[Job, StuartError](s"/v2/jobs/$job_id")
  }

  def getDriverPhoneNumber(delivery_id: Int): Either[StuartError, DriverPhoneNumber] = {
    executeWithoutRequest[DriverPhoneNumber, StuartError](s"/v2/deliveries/$delivery_id/phone_number")
  }

  def cancelJob(job_id: Int): Either[StuartError, Unit] = {
    executeWithoutRequestAndResponse[StuartError](
      s"/v2/jobs/$job_id/cancel",
      HttpMethods.POST
    )
  }

  def cancelDelivery(delivery_id: Int): Either[StuartError, Unit] = {
    executeWithoutRequestAndResponse[StuartError](
      s"/v2/deliveries/$delivery_id/cancel",
      HttpMethods.POST
    )
  }

  private[this] def jobQuery2Map(jobQuery: JobQuery): Map[String, String] = {
    import jobQuery._
    Map.empty ++ (status.toList match {
      case Nil => Map.empty
      case _ => Map("status" -> status.mkString(","))
    }) ++ (page match {
      case Some(p) => Map("page" -> p.toString)
      case None => Map.empty
    }) ++ (per_page match {
      case Some(pp) => Map("per_page" -> pp.toString)
      case None => Map.empty
    }) ++ (client_reference match {
      case Some(c) => Map("client_reference" -> c)
      case None => Map.empty
    }) ++ (active match {
      case Some(a) => Map("active" -> a.toString)
      case None => Map.empty
    }) ++ (order match {
      case Some(o) =>
        import JobListingOrder._
        Map("order" -> (o match {
          case _: start_inviting_at_desc.type => "start_inviting_at:desc"
          case _: pickup_at_desc.type => "pickup_at:desc"
          case _: pickup_at_asc.type => "pickup_at:asc"
          case _ => ""
        }))
      case None => Map.empty
    })
  }
}

object StuartApi {

  private[this] var instance: Option[StuartApi] = None

  def apply(sys: ActorSystem = ActorSystem()): StuartApi = {
    instance match {
      case Some(s) => s
      case _ =>
        val api = new StuartApi {
          implicit val system = sys
          implicit val ec = system.dispatcher
          implicit val mat = Materializer(system)
        }
        instance = Some(api)
        api
    }
  }

  case class StuartConfig(dryRun: Boolean,
                          apiClientId: String,
                          apiSecret: String,
                          oauth2Api: String = "/oauth/token",
                          zones: Map[String, Seq[String]]) extends Oauth2ApiConfig {
    lazy val baseUrl = {
      if(dryRun){
        "https://api.sandbox.stuart.com"
      }
      else{
        "https://api.stuart.com"
      }
    }

  }

}
