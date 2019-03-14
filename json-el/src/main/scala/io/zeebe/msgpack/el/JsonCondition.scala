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
package io.zeebe.msgpack.el

import org.agrona.DirectBuffer

trait Comparison {
  val x: JsonObject
  val y: JsonObject

  val variableNames = {
    (x, y) match {
      case (x: JsonPath, y: JsonPath) => Set(x.variableName, y.variableName)
      case (x: JsonPath, _)           => Set(x.variableName)
      case (_, y: JsonPath)           => Set(y.variableName)
      case _                          => Set[DirectBuffer]()
    }
  }
}

trait Operator {
  val x: JsonCondition
  val y: JsonCondition

  val variableNames = x.variableNames ++ y.variableNames
}

sealed trait JsonCondition {
  val variableNames: Set[DirectBuffer]
}

case class Equal(x: JsonObject, y: JsonObject) extends JsonCondition with Comparison

case class NotEqual(x: JsonObject, y: JsonObject) extends JsonCondition with Comparison

case class LessThan(x: JsonObject, y: JsonObject) extends JsonCondition with Comparison

case class LessOrEqual(x: JsonObject, y: JsonObject) extends JsonCondition with Comparison

case class GreaterThan(x: JsonObject, y: JsonObject) extends JsonCondition with Comparison

case class GreaterOrEqual(x: JsonObject, y: JsonObject) extends JsonCondition with Comparison

case class Disjunction(x: JsonCondition, y: JsonCondition) extends JsonCondition with Operator

case class Conjunction(x: JsonCondition, y: JsonCondition) extends JsonCondition with Operator
