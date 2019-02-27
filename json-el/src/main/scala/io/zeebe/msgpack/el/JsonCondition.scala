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

import scala.collection.mutable

abstract class Comparison extends JsonCondition {
  val x: JsonObject
  val y: JsonObject

  def variableNames: mutable.HashSet[DirectBuffer] = {
    val names = mutable.HashSet[DirectBuffer]()

    if (x.isInstanceOf[JsonPath]) {
      val path = x.asInstanceOf[JsonPath]
      names += path.variableName;
    }

    if (y.isInstanceOf[JsonPath]) {
      val path = y.asInstanceOf[JsonPath]
      names += path.variableName;
    }

    return names
  }
}

abstract class Operator extends JsonCondition {
  val x: JsonCondition
  val y: JsonCondition

  def variableNames: mutable.HashSet[DirectBuffer] = {
    var set: mutable.HashSet[DirectBuffer] = mutable.HashSet()
    set ++= x.variableNames
    set ++= y.variableNames
    return set
  }
}

sealed trait JsonCondition {
  def variableNames: mutable.HashSet[DirectBuffer]
}

case class Equal(x: JsonObject, y: JsonObject) extends Comparison

case class NotEqual(x: JsonObject, y: JsonObject) extends Comparison

case class LessThan(x: JsonObject, y: JsonObject) extends Comparison

case class LessOrEqual(x: JsonObject, y: JsonObject) extends Comparison

case class GreaterThan(x: JsonObject, y: JsonObject) extends Comparison

case class GreaterOrEqual(x: JsonObject, y: JsonObject) extends Comparison

case class Disjunction(x: JsonCondition, y: JsonCondition) extends Operator

case class Conjunction(x: JsonCondition, y: JsonCondition) extends Operator

