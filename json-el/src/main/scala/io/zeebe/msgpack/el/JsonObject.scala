/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.msgpack.el

import io.zeebe.msgpack.jsonpath.{ JsonPathQuery, JsonPathQueryCompiler }
import io.zeebe.msgpack.spec.MsgPackToken
import io.zeebe.msgpack.spec.MsgPackType._
import org.agrona.DirectBuffer
import io.zeebe.util.buffer.BufferUtil.bufferAsString

trait JsonConstant {
  val token = new MsgPackToken()
}

sealed trait JsonObject

case object JsonNull extends JsonObject with JsonConstant {
  token.setType(NIL)
}

case class JsonWholeNumber(value: Long) extends JsonObject with JsonConstant {
  token.setType(INTEGER)
  token.setValue(value)
}

case class JsonFloatingPointNumber(value: Double) extends JsonObject with JsonConstant {
  token.setType(FLOAT)
  token.setValue(value)
}

case class JsonBoolean(value: Boolean) extends JsonObject with JsonConstant {
  token.setType(BOOLEAN)
  token.setValue(value)
}

case class JsonString(value: DirectBuffer) extends JsonObject with JsonConstant {
  token.setType(STRING)
  token.setValue(value, 0, value.capacity())
}

case class JsonPath(variableName: DirectBuffer, path: List[String]) extends JsonObject {
  val jsonPath = (bufferAsString(variableName) :: path).mkString(".")
  val query: JsonPathQuery = new JsonPathQueryCompiler().compile(jsonPath)

  var id_ = -1

  def id(id: Int) = id_ = id

  def id = id_

}
