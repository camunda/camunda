/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.listview;

import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Objects;

public class VariableForListViewEntity
    implements ExporterEntity<VariableForListViewEntity>,
        PartitionedEntity<VariableForListViewEntity>,
        TenantOwned {

  private String id;
  private long key;
  private int partitionId;
  private Long processInstanceKey;
  private Long scopeKey;
  private String varName;
  private String varValue;
  private String tenantId;
  private Long position;

  private ListViewJoinRelation joinRelation =
      new ListViewJoinRelation(ListViewTemplate.VARIABLES_JOIN_RELATION);

  public static String getIdBy(final long scopeKey, final String name) {
    return String.format("%d-%s", scopeKey, name);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public VariableForListViewEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public VariableForListViewEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public VariableForListViewEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public VariableForListViewEntity setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public Long getScopeKey() {
    return scopeKey;
  }

  public VariableForListViewEntity setScopeKey(final Long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }

  public String getVarName() {
    return varName;
  }

  public VariableForListViewEntity setVarName(final String varName) {
    this.varName = varName;
    return this;
  }

  public String getVarValue() {
    return varValue;
  }

  public VariableForListViewEntity setVarValue(final String varValue) {
    this.varValue = varValue;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public VariableForListViewEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public ListViewJoinRelation getJoinRelation() {
    return joinRelation;
  }

  public VariableForListViewEntity setJoinRelation(final ListViewJoinRelation joinRelation) {
    this.joinRelation = joinRelation;
    return this;
  }

  public Long getPosition() {
    return position;
  }

  public VariableForListViewEntity setPosition(final Long position) {
    this.position = position;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        partitionId,
        processInstanceKey,
        scopeKey,
        varName,
        varValue,
        tenantId,
        position,
        joinRelation);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableForListViewEntity that = (VariableForListViewEntity) o;
    return Objects.equals(id, that.id)
        && partitionId == that.partitionId
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(scopeKey, that.scopeKey)
        && Objects.equals(varName, that.varName)
        && Objects.equals(varValue, that.varValue)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(position, that.position)
        && Objects.equals(joinRelation, that.joinRelation);
  }
}
