/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usertask;

import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Objects;

/** Represents variable with its value at the moment when task was completed. */
public class SnapshotTaskVariableEntity
    implements ExporterEntity<SnapshotTaskVariableEntity>,
        PartitionedEntity<SnapshotTaskVariableEntity>,
        TenantOwned {

  @BeforeVersion880 private String id;
  @BeforeVersion880 private String tenantId = DEFAULT_TENANT_IDENTIFIER;
  @BeforeVersion880 private long key;
  @BeforeVersion880 private int partitionId;
  @BeforeVersion880 private String taskId;
  @BeforeVersion880 private String name;
  @BeforeVersion880 private String value;
  @BeforeVersion880 private String fullValue;
  @BeforeVersion880 private boolean isPreview;
  @BeforeVersion880 private Long processInstanceKey;

  /** Attention! This field will be filled in only for data imported after v. 8.9.0. */
  @SinceVersion(value = "8.9.0", requireDefault = false)
  private Long rootProcessInstanceKey;

  public SnapshotTaskVariableEntity() {}

  @Override
  public String getId() {
    return id;
  }

  @Override
  public SnapshotTaskVariableEntity setId(final String id) {
    this.id = id;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public SnapshotTaskVariableEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public SnapshotTaskVariableEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public long getKey() {
    return key;
  }

  public SnapshotTaskVariableEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  public String getTaskId() {
    return taskId;
  }

  public SnapshotTaskVariableEntity setTaskId(final String taskId) {
    this.taskId = taskId;
    return this;
  }

  public String getName() {
    return name;
  }

  public SnapshotTaskVariableEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public SnapshotTaskVariableEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getFullValue() {
    return fullValue;
  }

  public SnapshotTaskVariableEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public boolean getIsPreview() {
    return isPreview;
  }

  public SnapshotTaskVariableEntity setIsPreview(final boolean preview) {
    isPreview = preview;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public SnapshotTaskVariableEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public SnapshotTaskVariableEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, tenantId, taskId, name, value, fullValue, isPreview, rootProcessInstanceKey);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SnapshotTaskVariableEntity that = (SnapshotTaskVariableEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(tenantId, that.tenantId)
        && isPreview == that.isPreview
        && Objects.equals(taskId, that.taskId)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(fullValue, that.fullValue)
        && Objects.equals(rootProcessInstanceKey, that.rootProcessInstanceKey);
  }
}
