/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.operation;

import io.camunda.operate.entities.OperationType;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;

import java.util.Objects;

public class CreateBatchOperationRequestDto {

  /**
   * Batch operation name
   */
  private String name;

  /**
   * Query to filter the process instances affected
   */
  private ListViewQueryDto query;

  /**
   * Operation type
   */
  private OperationType operationType;

  /**
   * Migration plan, only needed for process instance migration operation
   */
  private MigrationPlanDto migrationPlan;

  public String getName() {
    return name;
  }

  public CreateBatchOperationRequestDto setName(String name) {
    this.name = name;
    return this;
  }

  public ListViewQueryDto getQuery() {
    return query;
  }

  public CreateBatchOperationRequestDto setQuery(ListViewQueryDto query) {
    this.query = query;
    return this;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public CreateBatchOperationRequestDto setOperationType(OperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public MigrationPlanDto getMigrationPlan() {
    return migrationPlan;
  }

  public CreateBatchOperationRequestDto setMigrationPlan(MigrationPlanDto migrationPlan) {
    this.migrationPlan = migrationPlan;
    return this;
  }

  public CreateBatchOperationRequestDto() {
  }

  public CreateBatchOperationRequestDto(ListViewQueryDto query, OperationType operationType) {
    this.query = query;
    this.operationType = operationType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CreateBatchOperationRequestDto that = (CreateBatchOperationRequestDto) o;
    return Objects.equals(name, that.name) && Objects.equals(query, that.query) && operationType == that.operationType &&
        Objects.equals(migrationPlan, that.migrationPlan);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, query, operationType, migrationPlan);
  }

  @Override
  public String toString() {
    return "CreateBatchOperationRequestDto{" +
        "name='" + name + '\'' +
        ", query=" + query +
        ", operationType=" + operationType +
        ", migrationPlan=" + migrationPlan +
        '}';
  }
}
