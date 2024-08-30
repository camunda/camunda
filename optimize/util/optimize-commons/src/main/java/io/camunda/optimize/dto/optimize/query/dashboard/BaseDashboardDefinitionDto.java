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

@Data
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

  public static final class Fields {

    public static final String id = "id";
    public static final String name = "name";
    public static final String description = "description";
    public static final String lastModified = "lastModified";
    public static final String created = "created";
    public static final String owner = "owner";
    public static final String lastModifier = "lastModifier";
    public static final String collectionId = "collectionId";
    public static final String managementDashboard = "managementDashboard";
    public static final String instantPreviewDashboard = "instantPreviewDashboard";
    public static final String availableFilters = "availableFilters";
    public static final String refreshRateSeconds = "refreshRateSeconds";
  }
}
