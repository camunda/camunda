/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class DashboardDefinitionUpdateDto {

  protected String name;
  protected OffsetDateTime lastModified;
  protected String lastModifier;
  protected List<ReportLocationDto> reports;
  protected String collectionId;
  protected List<DashboardFilterDto<?>> availableFilters = new ArrayList<>();
}
