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
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class BusinessValueOverviewIndex<TBuilder>
    extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String TENANT_ID = BusinessValueOverviewDto.Fields.tenantId;
  public static final String PROCESS_DEFINITION_KEY =
      BusinessValueOverviewDto.Fields.processDefinitionKey;
  public static final String PROCESS_DEFINITION_NAME =
      BusinessValueOverviewDto.Fields.processDefinitionName;
  public static final String METRIC_RANGE = BusinessValueOverviewDto.Fields.metricRange;
  public static final String LAST_COMPUTED_AT = BusinessValueOverviewDto.Fields.lastComputedAt;
  public static final String CYCLE_TIME = BusinessValueOverviewDto.Fields.cycleTime;
  public static final String AUTOMATION_RATE = BusinessValueOverviewDto.Fields.automationRate;
  public static final String HAS_ANY_TARGET = BusinessValueOverviewDto.Fields.hasAnyTarget;
  public static final String TARGETS_SET = BusinessValueOverviewDto.Fields.targetsSet;
  public static final String TARGETS_MET = BusinessValueOverviewDto.Fields.targetsMet;

  public static final String VALUE = BusinessValueOverviewDto.Fields.value;
  public static final String TARGET = BusinessValueOverviewDto.Fields.target;
  public static final String MET = BusinessValueOverviewDto.Fields.met;

  @Override
  public String getIndexName() {
    return DatabaseConstants.BUSINESS_VALUE_OVERVIEW_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(TENANT_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_NAME, p -> p.keyword(k -> k))
        .properties(METRIC_RANGE, p -> p.keyword(k -> k))
        .properties(LAST_COMPUTED_AT, Property.of(p -> p.date(d -> d.format(OPTIMIZE_DATE_FORMAT))))
        .properties(
            CYCLE_TIME,
            p ->
                p.object(
                    o ->
                        o.properties(VALUE, v -> v.long_(l -> l))
                            .properties(TARGET, t -> t.long_(l -> l))
                            .properties(MET, m -> m.boolean_(b -> b))))
        .properties(
            AUTOMATION_RATE,
            p ->
                p.object(
                    o ->
                        o.properties(VALUE, v -> v.double_(d -> d))
                            .properties(TARGET, t -> t.integer(i -> i))
                            .properties(MET, m -> m.boolean_(b -> b))))
        .properties(HAS_ANY_TARGET, p -> p.boolean_(b -> b))
        .properties(TARGETS_SET, p -> p.integer(i -> i))
        .properties(TARGETS_MET, p -> p.integer(i -> i));
  }
}
