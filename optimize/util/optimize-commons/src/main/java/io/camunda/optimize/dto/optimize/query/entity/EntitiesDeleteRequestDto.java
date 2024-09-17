/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.entity;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class EntitiesDeleteRequestDto {

  @NotNull List<String> reports;
  @NotNull List<String> collections;
  @NotNull List<String> dashboards;

  public EntitiesDeleteRequestDto(
      @NotNull List<String> reports,
      @NotNull List<String> collections,
      @NotNull List<String> dashboards) {
    this.reports = reports;
    this.collections = collections;
    this.dashboards = dashboards;
  }

  public EntitiesDeleteRequestDto() {}
}
