/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class ExecutableLoopCharacteristics {

  private final boolean isSequential;

  private final JsonPathQuery inputCollection;
  private final Optional<DirectBuffer> inputElement;

  public ExecutableLoopCharacteristics(
      final boolean isSequential,
      final JsonPathQuery inputCollection,
      final Optional<DirectBuffer> inputElement) {
    this.isSequential = isSequential;
    this.inputCollection = inputCollection;
    this.inputElement = inputElement;
  }

  public boolean isSequential() {
    return isSequential;
  }

  public boolean isParallel() {
    return !isSequential;
  }

  public JsonPathQuery getInputCollection() {
    return inputCollection;
  }

  public Optional<DirectBuffer> getInputElement() {
    return inputElement;
  }

  @Override
  public String toString() {
    return "ExecutableLoopCharacteristics{"
        + "isSequential="
        + isSequential
        + ", inputCollection="
        + bufferAsString(inputCollection.getExpression())
        + ", inputElement="
        + inputElement
        + '}';
  }
}
