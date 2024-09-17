/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.combined;

import static io.camunda.optimize.dto.optimize.ReportConstants.DEFAULT_CONFIGURATION_COLOR;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class CombinedReportItemDto {

  private String id;
  @Builder.Default private String color = DEFAULT_CONFIGURATION_COLOR;

  public CombinedReportItemDto(final String id) {
    this.id = id;
  }

  public CombinedReportItemDto(String id, String color) {
    this.id = id;
    this.color = color;
  }

  protected CombinedReportItemDto() {}

  public static final class Fields {

    public static final String id = "id";
    public static final String color = "color";
  }
}
