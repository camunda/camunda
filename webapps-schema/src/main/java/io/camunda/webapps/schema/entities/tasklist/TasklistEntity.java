/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.tasklist;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Objects;

/* May be replaced by a common parent for both Tasklist and Operate */
public abstract class TasklistEntity<T extends TasklistEntity<T>> extends AbstractExporterEntity<T>
    implements TenantOwned {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long key;

  private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer partitionId;

  public TasklistEntity() {}

  public TasklistEntity(
      final String id, final Long key, final String tenantId, final int partitionId) {
    setId(id);
    this.key = key;
    this.tenantId = tenantId;
    this.partitionId = partitionId;
  }

  public TasklistEntity(final String id, final Long key, final String tenantId) {
    setId(id);
    this.key = key;
    this.tenantId = tenantId;
  }

  public Long getKey() {
    return key;
  }

  public T setKey(final Long key) {
    this.key = key;
    return (T) this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public T setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return (T) this;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public T setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return (T) this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, tenantId, partitionId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final TasklistEntity<?> that = (TasklistEntity<?>) o;
    return Objects.equals(key, that.key)
        && Objects.equals(partitionId, that.partitionId)
        && Objects.equals(tenantId, that.tenantId);
  }
}
