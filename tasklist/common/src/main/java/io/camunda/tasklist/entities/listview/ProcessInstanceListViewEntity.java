/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities.listview;

import com.fasterxml.jackson.annotation.JsonInclude;

public class ProcessInstanceListViewEntity {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String id;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer partitionId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String tenantId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private ListViewJoinRelation join;

  public String getId() {
    return id;
  }

  public ProcessInstanceListViewEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public ListViewJoinRelation getJoin() {
    return join;
  }

  public ProcessInstanceListViewEntity setJoin(final ListViewJoinRelation join) {
    this.join = join;
    return this;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public ProcessInstanceListViewEntity setPartitionId(final Integer partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public ProcessInstanceListViewEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }
}
