/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.pagination;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

@NoArgsConstructor
@AllArgsConstructor
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedDataExportDto {
  private String searchRequestId;
  private String message;
  private Integer numberOfRecordsInResponse;
  private long totalNumberOfRecords;
  private String reportId;
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
