/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.groupby.process.date;

import static io.camunda.optimize.service.db.os.report.filter.util.ModelElementFilterQueryUtilOS.createModelElementAggregationFilter;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.report.ExecutionContext;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public abstract class AbstractProcessGroupByFlowNodeDateInterpreterOS
    extends AbstractProcessGroupByModelElementDateInterpreterOS {

  protected abstract DefinitionService getDefinitionService();

  @Override
  protected String getPathToElementField() {
    return FLOW_NODE_INSTANCES;
  }

  @Override
  protected Query getFilterBoolQuery(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return createModelElementAggregationFilter(
            context.getReportData(), context.getFilterContext(), getDefinitionService())
        .build()
        .toQuery();
  }

  @Override
  protected Query getModelElementTypeFilterQuery() {
    return Query.of(q -> q.matchAll(m -> m));
  }
}
