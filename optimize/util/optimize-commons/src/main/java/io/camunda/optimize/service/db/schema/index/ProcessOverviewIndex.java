/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessDigestResponseDto;
import io.camunda.optimize.dto.optimize.query.processoverview.ProcessOverviewDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class ProcessOverviewIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 2;

  public static final String PROCESS_DEFINITION_KEY =
      ProcessOverviewDto.Fields.processDefinitionKey;
  public static final String OWNER = ProcessOverviewDto.Fields.owner;
  public static final String DIGEST = ProcessOverviewDto.Fields.digest;
  public static final String LAST_KPI_EVALUATION =
      ProcessOverviewDto.Fields.lastKpiEvaluationResults;
  public static final String ENABLED = ProcessDigestResponseDto.Fields.enabled;
  public static final String KPI_REPORT_RESULTS = ProcessDigestDto.Fields.kpiReportResults;

  @Override
  public String getIndexName() {
    return DatabaseConstants.PROCESS_OVERVIEW_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {

    return builder
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(OWNER, p -> p.keyword(k -> k))
        .properties(LAST_KPI_EVALUATION, p -> p.object(o -> o.dynamic(DynamicMapping.True)))
        .properties(
            DIGEST,
            p ->
                p.object(
                    o ->
                        o.properties(ENABLED, p2 -> p2.boolean_(b -> b))
                            .properties(
                                KPI_REPORT_RESULTS,
                                p2 -> p2.object(b -> b.dynamic(DynamicMapping.True)))));
  }
}
