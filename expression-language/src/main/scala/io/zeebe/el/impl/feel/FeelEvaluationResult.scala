/**
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.el.impl.feel

import java.{lang, util}

import io.zeebe.el.{EvaluationResult, Expression, ResultType}
import io.zeebe.util.buffer.BufferUtil.cloneBuffer
import org.agrona.DirectBuffer
import org.camunda.feel.syntaxtree._

import scala.collection.JavaConverters._

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

  override def getList: util.List[DirectBuffer] = result match {
    case ValList(array) => array.map(value => cloneBuffer(messagePackTransformer(value))).asJava
    case _ => null
  }
}
