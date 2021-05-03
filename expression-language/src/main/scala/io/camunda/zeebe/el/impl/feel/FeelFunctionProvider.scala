/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.el.impl.feel

import org.camunda.feel.context.FunctionProvider
import org.camunda.feel.syntaxtree._

class FeelFunctionProvider extends FunctionProvider {

  override lazy val functionNames: Iterable[String] = functions.keys

  private val functions: Map[String, List[ValFunction]] = Map(
    "cycle" -> List(cycleFunction, cycleInfiniteFunction)
  )

  override def getFunctions(name: String): List[ValFunction] = functions.getOrElse(name, Nil)

  private def cycleFunction = ValFunction(
    params = List("repetitions", "interval"),
    invoke = {
      case List(ValNull, ValDayTimeDuration(duration)) => ValString("R/%s".format(duration))
      case List(ValNull, ValYearMonthDuration(duration)) => ValString("R/%s".format(duration))
      case List(ValNumber(repetitions), ValDayTimeDuration(duration)) =>
        ValString("R%d/%S".format(repetitions.toInt, duration))
      case List(ValNumber(repetitions), ValYearMonthDuration(duration)) =>
        ValString("R%d/%S".format(repetitions.toInt, duration))
      case List(e: ValError, _) => e
      case List(_, e: ValError) => e
      case List(x, y) => ValError(s"cycle function expected a repetitions (number) and an interval (duration) parameter, but found '$x' and '$y'")
      case args => ValError(s"cycle function expected a repetitions (number) and an interval (duration) parameter, but found '$args'")
    }
  )

  private def cycleInfiniteFunction = ValFunction(
    params = List("interval"),
    invoke = {
      case List(ValDayTimeDuration(duration)) => ValString("R/%s".format(duration))
      case List(ValYearMonthDuration(duration)) => ValString("R/%s".format(duration))
      case List(e: ValError) => e
      case List(x) => ValError(s"cycle function expected an interval (duration) parameter, but found '$x'")
      case args => ValError(s"cycle function expected an interval (duration) parameter, but found '$args'")
    }
  )
}
