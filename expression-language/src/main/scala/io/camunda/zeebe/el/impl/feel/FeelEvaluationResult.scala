/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.el.impl.feel

import io.camunda.zeebe.el.{EvaluationResult, Expression, ResultType}
import io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer
import org.agrona.DirectBuffer
import org.camunda.feel.syntaxtree._

import java.time.{Duration, Period, ZoneId, ZonedDateTime}
import java.{lang, util}
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
    case _: ValDayTimeDuration => ResultType.DURATION
    case _: ValYearMonthDuration => ResultType.PERIOD
    case _: ValDateTime => ResultType.DATE_TIME
    case _: ValLocalDateTime => ResultType.DATE_TIME
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

  override def getDuration: Duration = result match {
    case ValDayTimeDuration(duration) => duration
    case _ => null
  }

  override def getPeriod: Period = result match {
    case ValYearMonthDuration(period) => period
    case _ => null
  }

  override def getDateTime: ZonedDateTime = result match {
    case ValDateTime(dateTime) => dateTime
    case ValLocalDateTime(dateTime) => dateTime.atZone(ZoneId.systemDefault())
    case _ => null
  }

  override def getList: util.List[DirectBuffer] = result match {
    case ValList(array) => array.map(value => cloneBuffer(messagePackTransformer(value))).asJava
    case _ => null
  }

  override def getListOfStrings: util.List[String] = result match {
    case ValList(array) => array.map {
      case ValString(string) => string
      case _ => null
    }.foldLeft(List[String]())((acc, next) => {
      if (acc == null) {
        acc
      } else if (next == null) {
        null
      } else {
        acc :+ next
      }
    }).asJava
  }
}
