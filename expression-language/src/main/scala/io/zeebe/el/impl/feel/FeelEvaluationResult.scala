/**
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.el.impl.feel

import java.lang

import io.zeebe.el.{EvaluationResult, Expression, ResultType}
import org.agrona.DirectBuffer
import org.camunda.feel.interpreter.{Val, ValBoolean, ValContext, ValList, ValNull, ValNumber, ValString}

class FeelEvaluationResult(
                            expression: Expression,
                            result: Val,
                            messagePackTransformer: Val => DirectBuffer)
  extends EvaluationResult {

  override def getExpression: String = expression.getExpression

  override def isFailure: Boolean = false

  override def getFailureMessage: String = null

  override def getType: ResultType = result match {
    case ValNull => ResultType.NULL
    case _: ValBoolean => ResultType.BOOLEAN
    case _: ValNumber => ResultType.NUMBER
    case _: ValString => ResultType.STRING
    case _: ValList => ResultType.ARRAY
    case _: ValContext => ResultType.OBJECT
    case _ => null
  }

  override def toBuffer: DirectBuffer = messagePackTransformer(result)

  override def getString: String = result match {
    case ValString(string) => string
    case _ => null
  }

  override def getBoolean: lang.Boolean = result match {
    case ValBoolean(boolean) => boolean
    case _ => null
  }

  override def getNumber: Number = result match {
    case ValNumber(number) => number
    case _ => null
  }
}
