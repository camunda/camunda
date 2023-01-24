/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.dashboard;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.dashboard.filter.DashboardFilterDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@FieldNameConstants
public class BaseDashboardDefinitionDto {
  protected String id;
  protected String name;
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
