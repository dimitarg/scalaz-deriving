// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/lgpl-3.0.en.html

package jsonformat.benchmarks

import scalaz.{ @@, deriving, Tags }
import jsonformat._

package s {
  final case class TradeTemplate(
    otc: Option[Boolean] @@ Tags.Last
  )
  object TradeTemplate {
    implicit val encoder: JsEncoder[TradeTemplate] = DerivedJsEncoder.gen
    implicit val decoder: JsDecoder[TradeTemplate] = DerivedProductJsDecoder.gen
  }
}

package m {
  @deriving(JsEncoder, JsDecoder)
  final case class TradeTemplate(
    otc: Option[Boolean] @@ Tags.Last
  )
}
