/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.operation;

import io.camunda.operate.entities.OperationType;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;

public class CreateBatchOperationRequestDto {

  public CreateBatchOperationRequestDto() {
  }

  public CreateBatchOperationRequestDto(ListViewQueryDto query, OperationType operationType) {
    this.query = query;
    this.operationType = operationType;
  }

  private ListViewQueryDto query;

  private OperationType operationType;

  /**
   * Batch operation name.
   */
  private String name;

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

  public String getName() {
    return name;
  }

  public CreateBatchOperationRequestDto setName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    CreateBatchOperationRequestDto that = (CreateBatchOperationRequestDto) o;

    if (query != null ? !query.equals(that.query) : that.query != null)
      return false;
    if (operationType != that.operationType)
      return false;
    return name != null ? name.equals(that.name) : that.name == null;

  }

  @Override
  public int hashCode() {
    int result = query != null ? query.hashCode() : 0;
    result = 31 * result + (operationType != null ? operationType.hashCode() : 0);
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "CreateBatchOperationRequestDto{" + "query=" + query + ", operationType=" + operationType + ", name='" + name + '\'' + '}';
  }
}
