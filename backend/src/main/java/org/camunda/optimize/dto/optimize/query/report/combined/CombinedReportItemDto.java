/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.combined;

import lombok.Data;

import static org.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_CONFIGURATION_COLOR;

@Data
public class CombinedReportItemDto {

  private String id;
  private String color = DEFAULT_CONFIGURATION_COLOR;

  protected CombinedReportItemDto() {
  }

  public CombinedReportItemDto(String id, String color) {
    this(id);
    this.color = color;
  }

  public CombinedReportItemDto(String id) {
    this.id = id;
  }
}
