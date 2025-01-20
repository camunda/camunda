/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.tasklist;

import com.fasterxml.jackson.annotation.JsonInclude;

public class TaskProcessInstanceEntity extends TasklistEntity<TaskProcessInstanceEntity> {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long processInstanceId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private TaskJoinRelationship join;

  public TaskProcessInstanceEntity() {}

  public TaskProcessInstanceEntity(
      final String id, final Long key, final String tenantId, final int partitionId) {
    super(id, key, tenantId, partitionId);
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
}
