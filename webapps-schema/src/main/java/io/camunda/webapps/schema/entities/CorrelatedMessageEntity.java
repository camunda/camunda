/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import java.util.Objects;

public class CorrelatedMessageEntity implements ExporterEntity<CorrelatedMessageEntity>, TenantOwned {

  private String id;
  private Long key;
  private Long messageKey;
  private String messageName;
  private String correlationKey;
  private Long processInstanceKey;
  private Long flowNodeInstanceKey;
  private String startEventId;
  private String bpmnProcessId;
  private String variables;
  private String tenantId = DEFAULT_TENANT_IDENTIFIER;
  private OffsetDateTime dateTime;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public CorrelatedMessageEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public Long getKey() {
    return key;
  }

  public CorrelatedMessageEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public Long getMessageKey() {
    return messageKey;
  }

  public CorrelatedMessageEntity setMessageKey(final Long messageKey) {
    this.messageKey = messageKey;
    return this;
  }

  public String getMessageName() {
    return messageName;
  }

  public CorrelatedMessageEntity setMessageName(final String messageName) {
    this.messageName = messageName;
    return this;
  }

  public String getCorrelationKey() {
    return correlationKey;
  }

  public CorrelatedMessageEntity setCorrelationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public CorrelatedMessageEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getFlowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public CorrelatedMessageEntity setFlowNodeInstanceKey(final Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    return this;
  }

  public String getStartEventId() {
    return startEventId;
  }

  public CorrelatedMessageEntity setStartEventId(final String startEventId) {
    this.startEventId = startEventId;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public CorrelatedMessageEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getVariables() {
    return variables;
  }

  public CorrelatedMessageEntity setVariables(final String variables) {
    this.variables = variables;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public CorrelatedMessageEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  public CorrelatedMessageEntity setDateTime(final OffsetDateTime dateTime) {
    this.dateTime = dateTime;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CorrelatedMessageEntity that = (CorrelatedMessageEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(key, that.key)
        && Objects.equals(messageKey, that.messageKey)
        && Objects.equals(messageName, that.messageName)
        && Objects.equals(correlationKey, that.correlationKey)
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(flowNodeInstanceKey, that.flowNodeInstanceKey)
        && Objects.equals(startEventId, that.startEventId)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(variables, that.variables)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(dateTime, that.dateTime);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        messageKey,
        messageName,
        correlationKey,
        processInstanceKey,
        flowNodeInstanceKey,
        startEventId,
        bpmnProcessId,
        variables,
        tenantId,
        dateTime);
  }

  @Override
  public String toString() {
    return "CorrelatedMessageEntity{"
        + "id='"
        + id
        + '\''
        + ", key="
        + key
        + ", messageKey="
        + messageKey
        + ", messageName='"
        + messageName
        + '\''
        + ", correlationKey='"
        + correlationKey
        + '\''
        + ", processInstanceKey="
        + processInstanceKey
        + ", flowNodeInstanceKey="
        + flowNodeInstanceKey
        + ", startEventId='"
        + startEventId
        + '\''
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", variables='"
        + variables
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", dateTime="
        + dateTime
        + '}';
  }
}