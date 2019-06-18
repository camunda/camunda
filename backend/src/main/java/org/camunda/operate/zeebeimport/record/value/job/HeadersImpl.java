/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.record.value.job;

import java.util.Objects;
import io.zeebe.protocol.record.value.job.Headers;

public class HeadersImpl implements Headers {
  private String bpmnProcessId;
  private String elementId;
  private long elementInstanceKey;
  private long workflowInstanceKey;
  private long workflowKey;
  private int workflowDefinitionVersion;

  public HeadersImpl() {
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  @Override
  public long getWorkflowKey() {
    return workflowKey;
  }

  @Override
  public int getWorkflowDefinitionVersion() {
    return workflowDefinitionVersion;
  }

  public void setBpmnProcessId(String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public void setElementId(String elementId) {
    this.elementId = elementId;
  }

  public void setElementInstanceKey(long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
  }

  public void setWorkflowInstanceKey(long workflowInstanceKey) {
    this.workflowInstanceKey = workflowInstanceKey;
  }

  public void setWorkflowKey(long workflowKey) {
    this.workflowKey = workflowKey;
  }

  public void setWorkflowDefinitionVersion(int workflowDefinitionVersion) {
    this.workflowDefinitionVersion = workflowDefinitionVersion;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final HeadersImpl headers = (HeadersImpl) o;
    return elementInstanceKey == headers.elementInstanceKey
        && workflowInstanceKey == headers.workflowInstanceKey
        && workflowKey == headers.workflowKey
        && workflowDefinitionVersion == headers.workflowDefinitionVersion
        && Objects.equals(bpmnProcessId, headers.bpmnProcessId)
        && Objects.equals(elementId, headers.elementId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        bpmnProcessId, elementId, elementInstanceKey,
        workflowInstanceKey,
        workflowKey,
        workflowDefinitionVersion);
  }

  @Override
  public String toString() {
    return "HeadersImpl{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", elementId='"
        + elementId
        + '\''
        + ", elementInstanceKey="
        + elementInstanceKey
        + ", workflowInstanceKey="
        + workflowInstanceKey
        + ", workflowKey="
        + workflowKey
        + ", workflowDefinitionVersion="
        + workflowDefinitionVersion
        + '}';
  }
}
