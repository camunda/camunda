/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.plan.decision;

import static io.camunda.optimize.service.db.DatabaseConstants.DECISION_INSTANCE_MULTI_ALIAS;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.service.db.es.builders.OptimizeBoolQueryBuilderES;
import io.camunda.optimize.service.db.es.filter.DecisionQueryFilterEnhancerES;
import io.camunda.optimize.service.db.es.report.interpreter.plan.AbstractExecutionPlanInterpreterES;
import io.camunda.optimize.service.db.es.schema.index.DecisionInstanceIndexES;
import io.camunda.optimize.service.db.reader.DecisionDefinitionReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.plan.decision.DecisionExecutionPlanInterpreter;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.util.DefinitionQueryUtilES;
import io.camunda.optimize.service.util.InstanceIndexUtil;

public abstract class AbstractDecisionExecutionPlanInterpreterES
    extends AbstractExecutionPlanInterpreterES<DecisionReportDataDto, DecisionExecutionPlan>
    implements DecisionExecutionPlanInterpreter {

  protected abstract DecisionDefinitionReader getDecisionDefinitionReader();

  protected abstract DecisionQueryFilterEnhancerES getQueryFilterEnhancer();

  @Override
  public BoolQuery.Builder getBaseQueryBuilder(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final BoolQuery.Builder boolQueryBuilder = setupUnfilteredBaseQueryBuilder(context);
    getQueryFilterEnhancer()
        .addFilterToQuery(
            boolQueryBuilder, context.getReportData().getFilter(), context.getFilterContext());
    return boolQueryBuilder;
  }

  @Override
  protected BoolQuery.Builder setupUnfilteredBaseQueryBuilder(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context) {
    final BoolQuery.Builder definitionFilterQueryBuilder =
        new OptimizeBoolQueryBuilderES().minimumShouldMatch("1");

    // for decision reports only one (the first) definition is supported
    context.getReportData().getDefinitions().stream()
        .findFirst()
        .ifPresent(
            definitionDto ->
                definitionFilterQueryBuilder.should(
                    s ->
                        s.bool(
                            DefinitionQueryUtilES.createDefinitionQuery(
                                    definitionDto.getKey(),
                                    definitionDto.getVersions(),
                                    definitionDto.getTenantIds(),
                                    new DecisionInstanceIndexES(definitionDto.getKey()),
                                    getDecisionDefinitionReader()::getLatestVersionToKey)
                                .build())));
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
