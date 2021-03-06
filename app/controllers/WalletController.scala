package controllers

import javax.inject._

import infra.BadGatewayException
import play.api.libs.ws.{WSAuthScheme, WSClient}
import play.api.mvc._
import services.{AuthConfig, WalletConfig}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class WalletController @Inject()(ws: WSClient, walletConfig: WalletConfig, authConfig: AuthConfig, implicit val context: ExecutionContext) extends Controller {

  def get(): Action[AnyContent] = Action.async { request =>
    walletId(request)
      .flatMap {
        wallet
      }
  }

  private def wallet(walletId: String): Future[Result] = {
    ws.url(s"${walletConfig.url}/wallets/$walletId")
      .withAuth(authConfig.username, authConfig.password, WSAuthScheme.BASIC)
      .get()
      .map { resp =>
        if (resp.status == 200) Ok(resp.json).withSession("WalletId" -> walletId) else throw new CouldNotGetWalletException
      }
  }

  private def walletId(request: Request[_]): Future[String] = {
    controllers.walletId(request)
      .map {
        Future.successful
      }
      .getOrElse {
        ws.url(s"${walletConfig.url}/wallets")
          .withAuth(authConfig.username, authConfig.password, WSAuthScheme.BASIC)
          .post("{}")
          .map { resp =>
            if (resp.status == 201) {
              resp.header("Location").get.replaceFirst(".*/", "")
            } else {
              throw new FailedToCreateWalletException
            }
          }
          .flatMap { walletId =>
            ws.url(s"${walletConfig.url}/wallets/$walletId/transactions")
              .withAuth(authConfig.username, authConfig.password, WSAuthScheme.BASIC)
              .withHeaders("Content-Type" -> "application/json")
              .post("{\"amount\": 1000, \"category\": \"CREDIT\"}")
              .map { resp =>
                if (resp.status == 201) {
                  walletId
                } else {
                  throw new FailedToCreditWalletException
                }
              }
          }
      }
  }
}

class FailedToCreateWalletException extends BadGatewayException("failed to create wallet")

class FailedToCreditWalletException extends BadGatewayException("failed to credit wallet")

class CouldNotGetWalletException extends BadGatewayException("could not get wallet")
