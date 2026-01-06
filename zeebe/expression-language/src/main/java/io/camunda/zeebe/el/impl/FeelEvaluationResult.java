/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static scala.jdk.javaapi.CollectionConverters.asJava;

import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.EvaluationWarning;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ResultType;
import java.time.Duration;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValBoolean;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValDate;
import org.camunda.feel.syntaxtree.ValDateTime;
import org.camunda.feel.syntaxtree.ValDayTimeDuration;
import org.camunda.feel.syntaxtree.ValList;
import org.camunda.feel.syntaxtree.ValLocalDateTime;
import org.camunda.feel.syntaxtree.ValNull$;
import org.camunda.feel.syntaxtree.ValNumber;
import org.camunda.feel.syntaxtree.ValString;
import org.camunda.feel.syntaxtree.ValYearMonthDuration;

final class FeelEvaluationResult implements EvaluationResult {
  final Expression expression;
  final Val result;
  final List<EvaluationWarning> warnings;
  final Function<Val, DirectBuffer> messagePackTransformer;

  FeelEvaluationResult(
      final Expression expression,
      final Val result,
      final List<EvaluationWarning> warnings,
      final Function<Val, DirectBuffer> messagePackTransformer) {
    this.expression = expression;
    this.result = result;
    this.warnings = warnings;
    this.messagePackTransformer = messagePackTransformer;
  }

  @Override
  public String getExpression() {
    return expression.getExpression();
  }

  @Override
  public boolean isFailure() {
    return false;
  }

  @Override
  public String getFailureMessage() {
    return null;
  }

  @Override
  public List<EvaluationWarning> getWarnings() {
    return warnings;
  }

  @Override
  public ResultType getType() {
    return switch (result) {
      case final ValNull$ ignored -> ResultType.NULL;
      case final ValBoolean ignored -> ResultType.BOOLEAN;
      case final ValNumber ignored -> ResultType.NUMBER;
      case final ValString ignored -> ResultType.STRING;
      case final ValList ignored -> ResultType.ARRAY;
      case final ValContext ignored -> ResultType.OBJECT;
      case final ValDayTimeDuration ignored -> ResultType.DURATION;
      case final ValYearMonthDuration ignored -> ResultType.PERIOD;
      case final ValDate ignored -> ResultType.DATE;
      case final ValDateTime ignored -> ResultType.DATE_TIME;
      case final ValLocalDateTime ignored -> ResultType.DATE_TIME;
      default -> ResultType.UNKNOWN;
    };
  }

  @Override
  public DirectBuffer toBuffer() {
    return messagePackTransformer.apply(result);
  }

  @Override
  public String getString() {
    if (result instanceof final ValString s) {
      return s.value();
    } else {
      return null;
    }
  }

  @Override
  public Boolean getBoolean() {
    if (result instanceof final ValBoolean b) {
      return b.value();
    } else {
      return null;
    }
  }

  @Override
  public Number getNumber() {
    if (result instanceof final ValNumber n) {
      return n.value();
    } else {
      return null;
    }
  }

  @Override
  public Duration getDuration() {
    if (result instanceof final ValDayTimeDuration d) {
      return d.value();
    } else {
      return null;
    }
  }

  @Override
  public Period getPeriod() {
    if (result instanceof final ValYearMonthDuration d) {
      return d.value();
    } else {
      return null;
    }
  }

  @Override
  public ZonedDateTime getDateTime() {
    return switch (result) {
      case final ValDateTime dateTime -> dateTime.value();
      case final ValLocalDateTime localDateTime ->
          localDateTime.value().atZone(ZoneId.systemDefault());
      default -> null;
    };
  }

  @Override
  public List<DirectBuffer> getList() {
    if (result instanceof final ValList l) {
      return asJava(l.itemsAsSeq().map(v -> cloneBuffer(messagePackTransformer.apply(v))).toList());
    } else {
      return null;
    }
  }

  @Override
  public List<String> getListOfStrings() {
    if (result instanceof final ValList l && l.items().forall(ValString.class::isInstance)) {
      return asJava(l.itemsAsSeq().map(v -> ((ValString) v).value()).toList());
    } else {
      return null;
    }
  }
}
