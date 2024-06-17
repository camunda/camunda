/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.dashboard;

import io.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@FieldNameConstants
public class BaseDashboardDefinitionDto {
  protected String id;
  protected String name;
  protected String description;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected String collectionId;
  protected boolean managementDashboard = false;
  protected boolean instantPreviewDashboard = false;
  protected List<DashboardFilterDto<?>> availableFilters = new ArrayList<>();
  protected Long refreshRateSeconds;
}
