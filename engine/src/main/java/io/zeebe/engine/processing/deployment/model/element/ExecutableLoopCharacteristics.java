/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.element;

import io.zeebe.el.Expression;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class ExecutableLoopCharacteristics {

  private final boolean isSequential;

  private final Expression inputCollection;
  private final Optional<DirectBuffer> inputElement;

  private final Optional<DirectBuffer> outputCollection;
  private final Optional<Expression> outputElement;

  public ExecutableLoopCharacteristics(
      final boolean isSequential,
      final Expression inputCollection,
      final Optional<DirectBuffer> inputElement,
      final Optional<DirectBuffer> outputCollection,
      final Optional<Expression> outputElement) {
    this.isSequential = isSequential;
    this.inputCollection = inputCollection;
    this.inputElement = inputElement;
    this.outputCollection = outputCollection;
    this.outputElement = outputElement;
  }

  public boolean isSequential() {
    return isSequential;
  }

  public Expression getInputCollection() {
    return inputCollection;
  }

  public Optional<DirectBuffer> getInputElement() {
    return inputElement;
  }

  public Optional<DirectBuffer> getOutputCollection() {
    return outputCollection;
  }

  public Optional<Expression> getOutputElement() {
    return outputElement;
  }

  @Override
  public String toString() {
    return "ExecutableLoopCharacteristics{"
        + "isSequential="
        + isSequential
        + ", inputCollection="
        + inputCollection
        + ", inputElement="
        + inputElement
        + ", outputCollection="
        + outputCollection
        + ", outputElement="
        + outputElement
        + '}';
  }
}
