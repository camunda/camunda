/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.pagination;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

  public void setData(final Object data) {
    this.data = data;
    if (data == null) {
      numberOfRecordsInResponse = 0;
    } else if (data instanceof Collection) {
      numberOfRecordsInResponse = ((Collection<?>) data).size();
    } else {
      numberOfRecordsInResponse = 1;
    }
  }
}
