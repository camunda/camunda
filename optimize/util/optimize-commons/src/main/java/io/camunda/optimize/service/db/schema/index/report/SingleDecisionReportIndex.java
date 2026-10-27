/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index.report;

import static io.camunda.optimize.service.db.DatabaseConstants.SINGLE_DECISION_REPORT_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;

public abstract class SingleDecisionReportIndex<TBuilder> extends AbstractReportIndex<TBuilder> {

  public static final int VERSION = 10;

  @Override
  public String getIndexName() {
    return SINGLE_DECISION_REPORT_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  protected TypeMapping.Builder addReportTypeSpecificFields(
      final TypeMapping.Builder xContentBuilder) {
    return xContentBuilder.properties(
        DATA,
        p ->
            p.object(
                o ->
                    o.dynamic(DynamicMapping.True)
                        .properties(
                            DecisionReportDataDto.Fields.view,
                            Property.of(q -> q.object(k -> k.enabled(false))))
                        .properties(
                            DecisionReportDataDto.Fields.groupBy,
                            Property.of(q -> q.object(k -> k.enabled(false))))
                        .properties(
                            DecisionReportDataDto.Fields.distributedBy,
                            Property.of(q -> q.object(k -> k.enabled(false))))
                        .properties(
                            DecisionReportDataDto.Fields.filter,
                            Property.of(q -> q.object(k -> k.enabled(false))))
                        .properties(
                            CONFIGURATION,
                            Property.of(
                                q ->
                                    q.object(
                                        k ->
                                            k.dynamic(DynamicMapping.True)
                                                .properties(
                                                    XML,
                                                    Property.of(
                                                        v ->
                                                            v.text(
                                                                t ->
                                                                    t.index(Boolean.TRUE)
                                                                        .analyzer(
                                                                            "is_present_analyzer"))))
                                                .properties(
                                                    AGGREGATION_TYPES,
                                                    Property.of(
                                                        v ->
                                                            v.object(
                                                                t ->
                                                                    t.dynamic(
                                                                        DynamicMapping
                                                                            .True)))))))));
  }
}
