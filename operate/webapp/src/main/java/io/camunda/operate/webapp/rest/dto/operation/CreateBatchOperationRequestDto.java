/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.operation;

import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.List;
import java.util.Objects;

public class CreateBatchOperationRequestDto {

  /** Batch operation name */
  private String name;

  /** Query to filter the process instances affected */
  private ListViewQueryDto query;

  /** Operation type */
  private OperationType operationType;

  /** Migration plan, only needed for process instance migration operation */
  private MigrationPlanDto migrationPlan;

  /** Modifications, only needed for MODIFY_PROCESS_INSTANCE operation type */
  private List<Modification> modifications;

  public CreateBatchOperationRequestDto() {}

  public CreateBatchOperationRequestDto(
      final ListViewQueryDto query, final OperationType operationType) {
    this.query = query;
    this.operationType = operationType;
  }

  public String getName() {
    return name;
  }

  public CreateBatchOperationRequestDto setName(final String name) {
    this.name = name;
    return this;
  }

  public ListViewQueryDto getQuery() {
    return query;
  }

  public CreateBatchOperationRequestDto setQuery(final ListViewQueryDto query) {
    this.query = query;
    return this;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public CreateBatchOperationRequestDto setOperationType(final OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public MigrationPlanDto getMigrationPlan() {
    return migrationPlan;
  }

  public CreateBatchOperationRequestDto setMigrationPlan(final MigrationPlanDto migrationPlan) {
    this.migrationPlan = migrationPlan;
    return this;
  }

  public List<Modification> getModifications() {
    return modifications;
  }

  public CreateBatchOperationRequestDto setModifications(final List<Modification> modifications) {
    this.modifications = modifications;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, query, operationType, migrationPlan, modifications);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CreateBatchOperationRequestDto that = (CreateBatchOperationRequestDto) o;
    return Objects.equals(name, that.name)
        && Objects.equals(query, that.query)
        && operationType == that.operationType
        && Objects.equals(migrationPlan, that.migrationPlan)
        && Objects.equals(modifications, that.modifications);
  }

  @Override
  public String toString() {
    return "CreateBatchOperationRequestDto{"
        + "name='"
        + name
        + '\''
        + ", query="
        + query
        + ", operationType="
        + operationType
        + ", migrationPlan="
        + migrationPlan
        + ", modifications="
        + modifications
        + '}';
  }
}
