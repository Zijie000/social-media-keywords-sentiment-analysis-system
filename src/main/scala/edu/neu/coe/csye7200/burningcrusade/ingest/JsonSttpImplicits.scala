package edu.neu.coe.csye7200.burningcrusade.ingest
import sttp.client3._
import org.json4s._
import org.json4s.{StringInput, Formats, AsJsonInput}
import org.json4s.native.JsonMethods._
import org.json4s.jvalue2monadic

object JsonSttpImplicits:
    given sttpBackend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

    given jsonFormats: Formats = DefaultFormats

    given stringAsJson: AsJsonInput[String] with {
        override def toJsonInput(str: String): JsonInput = StringInput(str)
    }
    
