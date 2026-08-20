/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.stream.api.records.TypedRecord;

/**
 * Exception that can be thrown during processing of a command, in case the engine attempts to
 * directly activate a multi-instance body, which is not supported at this time.
 *
 * <p>This exception can be handled by the processor in {@link
 * io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor#tryHandleError(TypedRecord,
 * Throwable)}.
 */
public class UnsupportedMultiInstanceBodyActivationException extends RuntimeException {

  private static final String ERROR_MESSAGE =
      """
      Expected to activate element '%s', but it is a multi-instance element, \
      which is not supported at this time.""";

  private final String bpmnProcessId;
  private final String multiInstanceId;

  /**
   * Constructs a new exception for the case that a multi-instance body is activated directly by
   * engine, which is not supported at this time.
   *
   * @param multiInstanceId the element id of the multi-instance
   * @param bpmnProcessId the BPMN process id
   */
  UnsupportedMultiInstanceBodyActivationException(
      final String multiInstanceId, final String bpmnProcessId) {
    super(ERROR_MESSAGE.formatted(multiInstanceId));
    this.bpmnProcessId = bpmnProcessId;
    this.multiInstanceId = multiInstanceId;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public String getMultiInstanceId() {
    return multiInstanceId;
  }
}
