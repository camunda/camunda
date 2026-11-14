/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.data.AgentRunFilterDataDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.schema.index.AgentRunIndex;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/** Restricts agent run reports to the selected agents, models and providers. */
@Component
@Conditional(ElasticSearchCondition.class)
public class AgentRunQueryFilterES implements QueryFilterES<AgentRunFilterDataDto> {

  @Override
  public void addFilters(
      final BoolQuery.Builder query,
      final List<AgentRunFilterDataDto> filterData,
      final FilterContext filterContext) {
    if (filterData == null) {
      return;
    }
    for (final AgentRunFilterDataDto data : filterData) {
      addTermsFilter(query, AgentRunIndex.AGENT_ID, data.getAgentIds());
      addTermsFilter(query, AgentRunIndex.MODEL, data.getModels());
      addTermsFilter(query, AgentRunIndex.PROVIDER, data.getProviders());
    }
  }

  private static void addTermsFilter(
      final BoolQuery.Builder query, final String field, final List<String> values) {
    if (values == null || values.isEmpty()) {
      return;
    }
    query.filter(
        f ->
            f.terms(
                t ->
                    t.field(field)
                        .terms(tv -> tv.value(values.stream().map(FieldValue::of).toList()))));
  }
}
