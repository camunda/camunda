/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.pagination;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class PaginatedDataExportDto {
  private String searchRequestId;
  private Integer numberOfRecordsInResponse;
  private long totalNumberOfRecords;
  private Object data;

  public void setData(Object data) {
    this.data = data;
    if(data == null)
    {
      this.numberOfRecordsInResponse = 0;
    } else if(data instanceof Collection) {
      this.numberOfRecordsInResponse = ((Collection<?>) data).size();
    }
    else {
      this.numberOfRecordsInResponse = 1;
    }
  }

  @JsonIgnore
  public <T> T getDataAs(Class<T> expected) {
    return (T) this.data;
  }
}
