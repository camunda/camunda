/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usertask;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.TenantOwned;

public class TaskVariableEntity
    implements ExporterEntity<TaskVariableEntity>,
        PartitionedEntity<TaskVariableEntity>,
        TenantOwned {

  @BeforeVersion880 private String id;
  @BeforeVersion880 private long key;
  @BeforeVersion880 private String tenantId = DEFAULT_TENANT_IDENTIFIER;
  @BeforeVersion880 private int partitionId;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String name;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String value;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String fullValue;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean isTruncated;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long scopeKey;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long processInstanceId;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long position;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TaskJoinRelationship join;

  /** Attention! This field will be filled in only for data imported after v. 8.9.0. */
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long rootProcessInstanceKey;

  public TaskVariableEntity() {}

  @Override
  public String getId() {
    return id;
  }

  @Override
  public TaskVariableEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public TaskVariableEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public TaskVariableEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public TaskVariableEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getName() {
    return name;
  }

  public TaskVariableEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public TaskVariableEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getFullValue() {
    return fullValue;
  }

  public TaskVariableEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public Boolean getIsTruncated() {
    return isTruncated;
  }

  public TaskVariableEntity setIsTruncated(final Boolean truncated) {
    isTruncated = truncated;
    return this;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public TaskVariableEntity setScopeKey(final Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public Long getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskVariableEntity setProcessInstanceId(final Long processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public TaskJoinRelationship getJoin() {
    return join;
  }

  public TaskVariableEntity setJoin(final TaskJoinRelationship join) {
    this.join = join;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public TaskVariableEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public TaskVariableEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }
}
