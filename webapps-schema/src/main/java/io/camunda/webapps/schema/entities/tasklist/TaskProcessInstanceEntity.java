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

public class TaskProcessInstanceEntity extends AbstractExporterEntity<TaskProcessInstanceEntity>
    implements TenantOwned {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long processInstanceId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TaskJoinRelationship join;

  private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  public TaskProcessInstanceEntity() {}

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

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public TaskProcessInstanceEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), join, tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof final TaskProcessInstanceEntity that)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return Objects.equals(join, that.join) && Objects.equals(tenantId, that.tenantId);
  }
}
