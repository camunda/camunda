/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class BusinessValueTargetIndex<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String PROCESS_DEFINITION_KEY =
      BusinessValueTargetDto.Fields.processDefinitionKey;
  public static final String TENANT_ID = BusinessValueTargetDto.Fields.tenantId;
  public static final String CYCLE_TIME_TARGET_MILLIS =
      BusinessValueTargetDto.Fields.cycleTimeTargetMillis;
  public static final String CYCLE_TIME_TARGET_UNIT =
      BusinessValueTargetDto.Fields.cycleTimeTargetUnit;
  public static final String AUTOMATION_RATE_TARGET_PCT =
      BusinessValueTargetDto.Fields.automationRateTargetPct;
  public static final String UPDATED_AT = BusinessValueTargetDto.Fields.updatedAt;
  public static final String UPDATED_BY = BusinessValueTargetDto.Fields.updatedBy;

  @Override
  public String getIndexName() {
    return DatabaseConstants.BUSINESS_VALUE_TARGET_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(TENANT_ID, p -> p.keyword(k -> k))
        .properties(CYCLE_TIME_TARGET_MILLIS, p -> p.long_(l -> l))
        .properties(CYCLE_TIME_TARGET_UNIT, p -> p.keyword(k -> k))
        .properties(AUTOMATION_RATE_TARGET_PCT, p -> p.integer(i -> i))
        .properties(UPDATED_AT, Property.of(p -> p.date(d -> d.format(OPTIMIZE_DATE_FORMAT))))
        .properties(UPDATED_BY, p -> p.keyword(k -> k));
  }
}
