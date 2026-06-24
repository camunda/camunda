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
 * Exception that can be thrown during processing of a command, in case the engine found more than
 * one instance of a flow scope, but it expects only one. This exception can be handled by the
 * processor in {@link
 * io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor#tryHandleError(TypedRecord,
 * Throwable)}.
 */
public final class MultipleFlowScopeInstancesFoundException extends RuntimeException {

  private static final String ERROR_MESSAGE =
      "Expected to have zero or one instance of the flow scope '%s' but found multiple instances.";

  private final String bpmnProcessId;
  private final String flowScopeId;

  /**
   * Constructs a new exception for the case that a flow scope has more instances as expected.
   *
   * @param flowScopeId the element id of the flow scope
   * @param bpmnProcessId the BPMN process id
   */
  MultipleFlowScopeInstancesFoundException(final String flowScopeId, final String bpmnProcessId) {
    super(ERROR_MESSAGE.formatted(flowScopeId));
    this.bpmnProcessId = bpmnProcessId;
    this.flowScopeId = flowScopeId;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public String getFlowScopeId() {
    return flowScopeId;
  }
}
