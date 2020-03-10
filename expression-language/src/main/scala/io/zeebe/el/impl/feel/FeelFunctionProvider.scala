/**
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.el.impl.feel

import org.camunda.feel.context.Context.StaticContext
import org.camunda.feel.context.{Context, CustomFunctionProvider}
import org.camunda.feel.syntaxtree.{ValContext, ValError, ValFunction, ValNull}

class FeelFunctionProvider extends CustomFunctionProvider {

  override lazy val functionNames: Iterable[String] = functions.keys

  override def getFunction(name: String): Option[ValFunction] = functions.get(name)

  private val functions: Map[String, ValFunction] = Map(
    "appendTo" -> appendFunction
  )

  private def appendFunction = ValFunction(
    params = List("x", "y"),
    invoke = {
      case List(ValContext(x), ValContext(y)) => append(x, y)
      case List(ValNull, y: ValContext) => y
      case List(x: ValContext, ValNull) => x
      case List(ValError(_), y: ValContext) => y // ignore variable not found error
      case List(e: ValError, _) => e
      case List(_, e: ValError) => e
      case args => ValError(s"expected two contexts but found '$args'")
    }
  )

  private def append(x: Context, y: Context): ValContext = {
    val mergedVariables = x.variableProvider.getVariables ++ y.variableProvider.getVariables

    ValContext(StaticContext(variables = mergedVariables))
  }

}
