package app.softnetwork.stuart.launch

import app.softnetwork.api.server.Application

import app.softnetwork.stuart.server.StuartMainRoutes

/**
  * Created by smanciot on 19/04/2021.
  */
object StuartApplication extends Application with StuartMainRoutes {

  /**
    *
    * @return the banner to print to the console
    */
  override def banner: String =
    """
      | ____  _                    _
      |/ ___|| |_ _   _  __ _ _ __| |_
      |\___ \| __| | | |/ _` | '__| __|
      | ___) | |_| |_| | (_| | |  | |_
      ||____/ \__|\__,_|\__,_|_|   \__|
      |
      |
    """.stripMargin

}