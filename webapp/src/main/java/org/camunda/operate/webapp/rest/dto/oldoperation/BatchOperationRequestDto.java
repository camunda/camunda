/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto.oldoperation;

import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;

@Deprecated //OPE-786
public class BatchOperationRequestDto extends OperationRequestDto {

  public BatchOperationRequestDto() {
  }

  public BatchOperationRequestDto(List<ListViewQueryDto> queries) {
    this.queries = queries;
  }

  private List<ListViewQueryDto> queries = new ArrayList<>();

  public List<ListViewQueryDto> getQueries() {
    return queries;
  }

  public void setQueries(List<ListViewQueryDto> queries) {
    this.queries = queries;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    BatchOperationRequestDto that = (BatchOperationRequestDto) o;

    return queries != null ? queries.equals(that.queries) : that.queries == null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (queries != null ? queries.hashCode() : 0);
    return result;
  }
}
