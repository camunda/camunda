/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.INSTANT_DASHBOARD_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class InstantPreviewDashboardMetadataIndex<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String ID = InstantDashboardDataDto.Fields.instantDashboardId;
  public static final String DASHBOARD_ID = InstantDashboardDataDto.Fields.dashboardId;
  public static final String PROCESS_DEFINITION_KEY =
      InstantDashboardDataDto.Fields.processDefinitionKey;
  public static final String TEMPLATE_NAME = InstantDashboardDataDto.Fields.templateName;
  public static final String TEMPLATE_HASH = InstantDashboardDataDto.Fields.templateHash;

  @Override
  public String getIndexName() {
    return INSTANT_DASHBOARD_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {

    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(DASHBOARD_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(TEMPLATE_NAME, p -> p.keyword(k -> k))
        .properties(TEMPLATE_HASH, p -> p.long_(k -> k));
  }
}
