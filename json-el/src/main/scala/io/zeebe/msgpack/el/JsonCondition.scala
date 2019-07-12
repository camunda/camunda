/**
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
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
