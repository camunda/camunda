/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;

public final class ProcessElementProperties {

  private final String elementId;
  private final long elementInstanceKey;
  private final String bpmnProcessId;
  private final long processDefinitionKey;
  private final long processInstanceKey;
  private final int processVersion;
  private final String tenantId;

  private ProcessElementProperties(
      final String elementId,
      final long elementInstanceKey,
      final String bpmnProcessId,
      final long processDefinitionKey,
      final long processInstanceKey,
      final int processVersion,
      final String tenantId) {
    this.elementId = elementId;
    this.elementInstanceKey = elementInstanceKey;
    this.bpmnProcessId = bpmnProcessId;
    this.processDefinitionKey = processDefinitionKey;
    this.processInstanceKey = processInstanceKey;
    this.processVersion = processVersion;
    this.tenantId = tenantId;
  }

  public String getElementId() {
    return elementId;
  }

  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public int getProcessVersion() {
    return processVersion;
  }

  public String getTenantId() {
    return tenantId;
  }

  public static ProcessElementProperties from(final BpmnElementContext bpmnElementContext) {
    return new ProcessElementProperties(
        bufferAsString(bpmnElementContext.getElementId()),
        bpmnElementContext.getElementInstanceKey(),
        bufferAsString(bpmnElementContext.getBpmnProcessId()),
        bpmnElementContext.getProcessDefinitionKey(),
        bpmnElementContext.getProcessInstanceKey(),
        bpmnElementContext.getProcessVersion(),
        bpmnElementContext.getTenantId());
  }

  public static ProcessElementProperties from(final UserTaskRecord userTaskRecord) {
    return new ProcessElementProperties(
        userTaskRecord.getElementId(),
        userTaskRecord.getElementInstanceKey(),
        userTaskRecord.getBpmnProcessId(),
        userTaskRecord.getProcessDefinitionKey(),
        userTaskRecord.getProcessInstanceKey(),
        userTaskRecord.getProcessDefinitionVersion(),
        userTaskRecord.getTenantId());
  }
}
