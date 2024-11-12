/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.plan.decision;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;

import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.os.report.filter.DecisionQueryFilterEnhancerOS;
import io.camunda.optimize.service.db.os.report.interpreter.plan.AbstractExecutionPlanInterpreterOS;
import io.camunda.optimize.service.db.os.schema.index.DecisionInstanceIndexOS;
import io.camunda.optimize.service.db.os.util.DefinitionQueryUtilOS;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.decision.DecisionExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.util.InstanceIndexUtil;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;

public abstract class AbstractDecisionExecutionPlanInterpreterOS
    extends AbstractExecutionPlanInterpreterOS<DecisionReportDataDto, DecisionExecutionPlan>
    implements DecisionExecutionPlanInterpreter {

  protected abstract DecisionDefinitionReader getDecisionDefinitionReader();

  protected abstract DecisionQueryFilterEnhancerOS getQueryFilterEnhancer();

  @Override
  public BoolQuery.Builder baseQueryBuilder(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return unfilteredBaseQueryBuilder(context)
        .filter(
            getQueryFilterEnhancer()
                .filterQueries(context.getReportData().getFilter(), context.getFilterContext()));
  }

  @Override
  protected BoolQuery.Builder unfilteredBaseQueryBuilder(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final BoolQuery.Builder definitionFilterQueryBuilder =
        new BoolQuery.Builder().minimumShouldMatch("1");
    // for decision reports only one (the first) definition is supported
    context.getReportData().getDefinitions().stream()
        .findFirst()
        .ifPresent(
            definitionDto ->
                definitionFilterQueryBuilder.should(
                    DefinitionQueryUtilOS.createDefinitionQuery(
                            definitionDto.getKey(),
                            definitionDto.getVersions(),
                            definitionDto.getTenantIds(),
                            new DecisionInstanceIndexOS(definitionDto.getKey()),
                            getDecisionDefinitionReader()::getLatestVersionToKey)
                        .build()
                        .toQuery()));
    return definitionFilterQueryBuilder;
  }

  @Override
  protected String[] getIndexNames(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    return InstanceIndexUtil.getDecisionInstanceIndexAliasName(context.getReportData());
  }

  @Override
  protected String[] getMultiIndexAlias() {
    return new String[] {DECISION_INSTANCE_MULTI_ALIAS};
  }
}
