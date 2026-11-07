/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities;

/** PoC: one variable change with its value, as stored in the {@code variable-history} index. */
public class VariableHistoryEntity implements ExporterEntity<VariableHistoryEntity> {

  private String id;
  private long key;
  private int partitionId;
  private long position;
  private long timestamp;
  private String name;
  private long scopeKey;
  private long processInstanceKey;
  private Long rootProcessInstanceKey;
  private long processDefinitionKey;
  private String bpmnProcessId;
  private String tenantId;
  private String operation;
  private String actorType;
  private String actorId;
  private int valueSize;
  private boolean isPreview;
  private String value;
  private String fullValue;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public VariableHistoryEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public VariableHistoryEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public VariableHistoryEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getPosition() {
    return position;
  }

  public VariableHistoryEntity setPosition(final long position) {
    this.position = position;
    return this;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public VariableHistoryEntity setTimestamp(final long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  public String getName() {
    return name;
  }

  public VariableHistoryEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public long getScopeKey() {
    return scopeKey;
  }

  public VariableHistoryEntity setScopeKey(final long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public VariableHistoryEntity setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public VariableHistoryEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public VariableHistoryEntity setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public VariableHistoryEntity setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public VariableHistoryEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getOperation() {
    return operation;
  }

  public VariableHistoryEntity setOperation(final String operation) {
    this.operation = operation;
    return this;
  }

  public String getActorType() {
    return actorType;
  }

  public VariableHistoryEntity setActorType(final String actorType) {
    this.actorType = actorType;
    return this;
  }

  public String getActorId() {
    return actorId;
  }

  public VariableHistoryEntity setActorId(final String actorId) {
    this.actorId = actorId;
    return this;
  }

  public int getValueSize() {
    return valueSize;
  }

  public VariableHistoryEntity setValueSize(final int valueSize) {
    this.valueSize = valueSize;
    return this;
  }

  public boolean getIsPreview() {
    return isPreview;
  }

  public VariableHistoryEntity setIsPreview(final boolean isPreview) {
    this.isPreview = isPreview;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableHistoryEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getFullValue() {
    return fullValue;
  }

  public VariableHistoryEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }
}
