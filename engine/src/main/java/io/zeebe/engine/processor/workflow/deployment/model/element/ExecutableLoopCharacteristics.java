/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class ExecutableLoopCharacteristics {

  private final boolean isSequential;

  private final JsonPathQuery inputCollection;
  private final Optional<DirectBuffer> inputElement;

  private final Optional<DirectBuffer> outputCollection;
  private final Optional<JsonPathQuery> outputElement;

  public ExecutableLoopCharacteristics(
      final boolean isSequential,
      final JsonPathQuery inputCollection,
      final Optional<DirectBuffer> inputElement,
      final Optional<DirectBuffer> outputCollection,
      final Optional<JsonPathQuery> outputElement) {
    this.isSequential = isSequential;
    this.inputCollection = inputCollection;
    this.inputElement = inputElement;
    this.outputCollection = outputCollection;
    this.outputElement = outputElement;
  }

  public boolean isSequential() {
    return isSequential;
  }

  public JsonPathQuery getInputCollection() {
    return inputCollection;
  }

  public Optional<DirectBuffer> getInputElement() {
    return inputElement;
  }

  public Optional<DirectBuffer> getOutputCollection() {
    return outputCollection;
  }

  public Optional<JsonPathQuery> getOutputElement() {
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
