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
import java.util.Objects;

public class TaskProcessInstanceEntity
    implements ExporterEntity<TaskProcessInstanceEntity>,
        PartitionedEntity<TaskProcessInstanceEntity>,
        TenantOwned {

  @BeforeVersion880 private String id;
  @BeforeVersion880 private String tenantId = DEFAULT_TENANT_IDENTIFIER;
  @BeforeVersion880 private int partitionId;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long processInstanceId;

  @BeforeVersion880
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TaskJoinRelationship join;

  /** Attention! This field will be filled in only for data imported after v. 8.9.0. */
  @SinceVersion(value = "8.9.0", requireDefault = false)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long rootProcessInstanceKey;

  public TaskProcessInstanceEntity() {}

  @Override
  public String getId() {
    return id;
  }

  @Override
  public TaskProcessInstanceEntity setId(final String id) {
    this.id = id;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public TaskProcessInstanceEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public TaskProcessInstanceEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public TaskJoinRelationship getJoin() {
    return join;
  }

  public TaskProcessInstanceEntity setJoin(final TaskJoinRelationship join) {
    this.join = join;
    return this;
  }

  public Long getProcessInstanceId() {
    return processInstanceId;
  }

  public TaskProcessInstanceEntity setProcessInstanceId(final Long processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public TaskProcessInstanceEntity setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, tenantId, partitionId, processInstanceId, join, rootProcessInstanceKey);
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof final TaskProcessInstanceEntity that)) {
      return false;
    }
    return partitionId == that.partitionId
        && Objects.equals(rootProcessInstanceKey, that.rootProcessInstanceKey)
        && Objects.equals(id, that.id)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(processInstanceId, that.processInstanceId)
        && Objects.equals(join, that.join);
  }
}
