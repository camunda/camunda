/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.groupby.process.date;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createModelElementAggregationFilter;
import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createUserTaskFlowNodeTypeFilter;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.report.ExecutionContext;
import org.elasticsearch.index.query.QueryBuilder;

public abstract class AbstractProcessGroupByUserTaskDateInterpreterES
    extends AbstractProcessGroupByModelElementDateInterpreterES {

  protected abstract DefinitionService getDefinitionService();

  @Override
  protected QueryBuilder getFilterQuery(final ExecutionContext<ProcessReportDataDto, ?> context) {
    return createModelElementAggregationFilter(
        context.getReportData(), context.getFilterContext(), getDefinitionService());
  }

  @Override
  protected QueryBuilder getModelElementTypeFilterQuery() {
    return createUserTaskFlowNodeTypeFilter();
  }

  @Override
  protected String getPathToElementField() {
    return FLOW_NODE_INSTANCES;
  }
}
