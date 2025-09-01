/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.function.Function;

public class CorrelatedMessageDbModel implements Copyable<CorrelatedMessageDbModel> {
  private Long key;
  private Long messageKey;
  private String messageName;
  private String correlationKey;
  private Long processInstanceKey;
  private Long flowNodeInstanceKey;
  private String startEventId;
  private String bpmnProcessId;
  private String variables;
  private String tenantId;
  private OffsetDateTime dateTime;
  private int partitionId;
  private OffsetDateTime historyCleanupDate;

  public CorrelatedMessageDbModel(final Long key) {
    this.key = key;
  }

  public CorrelatedMessageDbModel(
      final Long key,
      final Long messageKey,
      final String messageName,
      final String correlationKey,
      final Long processInstanceKey,
      final Long flowNodeInstanceKey,
      final String startEventId,
      final String bpmnProcessId,
      final String variables,
      final String tenantId,
      final OffsetDateTime dateTime,
      final int partitionId,
      final OffsetDateTime historyCleanupDate) {
    this.key = key;
    this.messageKey = messageKey;
    this.messageName = messageName;
    this.correlationKey = correlationKey;
    this.processInstanceKey = processInstanceKey;
    this.flowNodeInstanceKey = flowNodeInstanceKey;
    this.startEventId = startEventId;
    this.bpmnProcessId = bpmnProcessId;
    this.variables = variables;
    this.tenantId = tenantId;
    this.dateTime = dateTime;
    this.partitionId = partitionId;
    this.historyCleanupDate = historyCleanupDate;
  }

  public Long key() {
    return key;
  }

  public void key(final Long key) {
    this.key = key;
  }

  public Long messageKey() {
    return messageKey;
  }

  public void messageKey(final Long messageKey) {
    this.messageKey = messageKey;
  }

  public String messageName() {
    return messageName;
  }

  public void messageName(final String messageName) {
    this.messageName = messageName;
  }

  public String correlationKey() {
    return correlationKey;
  }

  public void correlationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
  }

  public Long processInstanceKey() {
    return processInstanceKey;
  }

  public void processInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public Long flowNodeInstanceKey() {
    return flowNodeInstanceKey;
  }

  public void flowNodeInstanceKey(final Long flowNodeInstanceKey) {
    this.flowNodeInstanceKey = flowNodeInstanceKey;
  }

  public String startEventId() {
    return startEventId;
  }

  public void startEventId(final String startEventId) {
    this.startEventId = startEventId;
  }

  public String bpmnProcessId() {
    return bpmnProcessId;
  }

  public void bpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
  }

  public String variables() {
    return variables;
  }

  public void variables(final String variables) {
    this.variables = variables;
  }

  public String tenantId() {
    return tenantId;
  }

  public void tenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public OffsetDateTime dateTime() {
    return dateTime;
  }

  public void dateTime(final OffsetDateTime dateTime) {
    this.dateTime = dateTime;
  }

  public int partitionId() {
    return partitionId;
  }

  public void partitionId(final int partitionId) {
    this.partitionId = partitionId;
  }

  public OffsetDateTime historyCleanupDate() {
    return historyCleanupDate;
  }

  public void historyCleanupDate(final OffsetDateTime historyCleanupDate) {
    this.historyCleanupDate = historyCleanupDate;
  }

  @Override
  public CorrelatedMessageDbModel copy() {
    return new CorrelatedMessageDbModel(
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
        dateTime,
        partitionId,
        historyCleanupDate);
  }

  @Override
  public void copyFrom(final CorrelatedMessageDbModel other) {
    this.key = other.key;
    this.messageKey = other.messageKey;
    this.messageName = other.messageName;
    this.correlationKey = other.correlationKey;
    this.processInstanceKey = other.processInstanceKey;
    this.flowNodeInstanceKey = other.flowNodeInstanceKey;
    this.startEventId = other.startEventId;
    this.bpmnProcessId = other.bpmnProcessId;
    this.variables = other.variables;
    this.tenantId = other.tenantId;
    this.dateTime = other.dateTime;
    this.partitionId = other.partitionId;
    this.historyCleanupDate = other.historyCleanupDate;
  }

  public static CorrelatedMessageDbModel of(
      final Function<Builder, ObjectBuilder<CorrelatedMessageDbModel>> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static class Builder implements ObjectBuilder<CorrelatedMessageDbModel> {
    private Long key;
    private Long messageKey;
    private String messageName;
    private String correlationKey;
    private Long processInstanceKey;
    private Long flowNodeInstanceKey;
    private String startEventId;
    private String bpmnProcessId;
    private String variables;
    private String tenantId;
    private OffsetDateTime dateTime;
    private int partitionId;
    private OffsetDateTime historyCleanupDate;

    public Builder key(final Long key) {
      this.key = key;
      return this;
    }

    public Builder messageKey(final Long messageKey) {
      this.messageKey = messageKey;
      return this;
    }

    public Builder messageName(final String messageName) {
      this.messageName = messageName;
      return this;
    }

    public Builder correlationKey(final String correlationKey) {
      this.correlationKey = correlationKey;
      return this;
    }

    public Builder processInstanceKey(final Long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
      return this;
    }

    public Builder flowNodeInstanceKey(final Long flowNodeInstanceKey) {
      this.flowNodeInstanceKey = flowNodeInstanceKey;
      return this;
    }

    public Builder startEventId(final String startEventId) {
      this.startEventId = startEventId;
      return this;
    }

    public Builder bpmnProcessId(final String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
      return this;
    }

    public Builder variables(final String variables) {
      this.variables = variables;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder dateTime(final OffsetDateTime dateTime) {
      this.dateTime = dateTime;
      return this;
    }

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder historyCleanupDate(final OffsetDateTime historyCleanupDate) {
      this.historyCleanupDate = historyCleanupDate;
      return this;
    }

    @Override
    public CorrelatedMessageDbModel build() {
      return new CorrelatedMessageDbModel(
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
          dateTime,
          partitionId,
          historyCleanupDate);
    }
  }
}