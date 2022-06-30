/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.util.buffer.BufferWriter;

public class ProcessingResult {

  private static final ProcessingResult EMPTY = new ProcessingResult();
  private BufferWriter bufferWriter;

  public ProcessingResult() {}

  public ProcessingResult(final BufferWriter bufferWriter) {
    this.bufferWriter = bufferWriter;
  }

  public static ProcessingResult empty() {
    return EMPTY;
  }

  public BufferWriter getRecords() {
    return bufferWriter;
  }
}
