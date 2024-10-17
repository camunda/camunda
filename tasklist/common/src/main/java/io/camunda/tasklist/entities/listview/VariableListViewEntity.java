/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.entities.listview;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.tasklist.entities.VariableEntity;

public class VariableListViewEntity {
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long key;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String id;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String name;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String value;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String fullValue;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Boolean isPreview;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String scopeKey;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Integer partitionId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String tenantId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String processInstanceId;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private ListViewJoinRelation join;

  public VariableListViewEntity() {}

  public VariableListViewEntity(final VariableEntity entity) {
    setKey(entity.getKey());
    setValue(entity.getValue());
    setFullValue(entity.getFullValue());
    setName(entity.getName());
    setIsPreview(entity.getIsPreview());
    setScopeKey(entity.getScopeFlowNodeId());
    setId(entity.getId());
    setPartitionId(entity.getPartitionId());
    setTenantId(entity.getTenantId());
    setProcessInstanceKey(entity.getProcessInstanceId());

    // Set the join relation
    final ListViewJoinRelation joinRelation = new ListViewJoinRelation();
    setJoin(joinRelation);
  }

  // Add getter and setters
  public Long getKey() {
    return key;
  }

  public VariableListViewEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public String getId() {
    return id;
  }

  public VariableListViewEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public VariableListViewEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableListViewEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getFullValue() {
    return fullValue;
  }

  public VariableListViewEntity setFullValue(final String fullValue) {
    this.fullValue = fullValue;
    return this;
  }

  public Boolean getIsPreview() {
    return isPreview;
  }

  public VariableListViewEntity setIsPreview(final Boolean isPreview) {
    this.isPreview = isPreview;
    return this;
  }

  public String getScopeKey() {
    return scopeKey;
  }

  public VariableListViewEntity setScopeKey(final String scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public Integer getPartitionId() {
    return partitionId;
  }

  public VariableListViewEntity setPartitionId(final Integer partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public VariableListViewEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public String getProcessInstanceId() {
    return processInstanceId;
  }

  public VariableListViewEntity setProcessInstanceKey(final String processInstanceId) {
    this.processInstanceId = processInstanceId;
    return this;
  }

  public ListViewJoinRelation getJoin() {
    return join;
  }

  public VariableListViewEntity setJoin(final ListViewJoinRelation join) {
    this.join = join;
    return this;
  }

  public static VariableListViewEntity createFrom(
      final String tenantId,
      final String id,
      final String name,
      final String value,
      final String scopeKey,
      final int variableSizeThreshold,
      final ListViewJoinRelation listViewJoinRelation) {
    final VariableListViewEntity entity = new VariableListViewEntity().setId(id).setName(name);
    if (value.length() > variableSizeThreshold) {
      // store preview
      entity.setValue(value.substring(0, variableSizeThreshold));
      entity.setIsPreview(true);
    } else {
      entity.setIsPreview(false);
      entity.setValue(value);
    }
    entity.setScopeKey(scopeKey);
    entity.setFullValue(value);
    entity.setTenantId(tenantId);
    entity.setJoin(listViewJoinRelation);
    return entity;
  }
}
