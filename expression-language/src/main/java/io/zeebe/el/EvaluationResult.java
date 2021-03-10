/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.el;

import java.time.Duration;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.List;
import org.agrona.DirectBuffer;

/** The result of an expression evaluation. */
public interface EvaluationResult {

  /** @return the (raw) expression as string */
  String getExpression();

  /** @return {@code true} if the evaluation was not successful */
  boolean isFailure();

  /**
   * Returns the reason why the evaluation failed. Use {@link #isFailure()} to check if the
   * evaluation failed or not.
   *
   * @return the failure message if the evaluation failed, otherwise {@code null}
   */
  String getFailureMessage();

  /** @return the type of the evaluation result, or {@code null} if the evaluation failed */
  ResultType getType();

  /**
   * @return the evaluation result as MessagePack encoded buffer, or {@code null} if the evaluation
   *     failed
   */
  DirectBuffer toBuffer();

  /**
   * Use {@link #getType()} to check if the result is of the type {@link ResultType#STRING}.
   *
   * @return the evaluation result, or {@code null} if it is not a string
   */
  String getString();

  /**
   * Use {@link #getType()} to check if the result is of the type {@link ResultType#BOOLEAN}.
   *
   * @return the evaluation result, or {@code null} if it is not a boolean
   */
  Boolean getBoolean();

  /**
   * Use {@link #getType()} to check if the result is of the type {@link ResultType#NUMBER}.
   *
   * @return the evaluation result, or {@code null} if it is not a number
   */
  Number getNumber();

  /**
   * Use {@link #getType()} to check if the result is of the type {@link ResultType#DURATION}.
   *
   * @return the evaluation result, or {@code null} if it is not a duration
   */
  Duration getDuration();

  /**
   * Use {@link #getType()} to check if the result is of the type {@link ResultType#PERIOD}.
   *
   * @return the evaluation result, or {@code null} if it is not a period
   */
  Period getPeriod();

  /**
   * Use {@link #getType()} to check if the result is of the type {@link ResultType#DATE_TIME}.
   *
   * @return the evaluation result, or {@code null} if it is not a date time
   */
  ZonedDateTime getDateTime();

  /**
   * Use {@link #getType()} to check if the result is of the type {@link ResultType#ARRAY}.
   *
   * @return the evaluation result, or {@code null} if it is not an array
   */
  List<DirectBuffer> getList();
}
