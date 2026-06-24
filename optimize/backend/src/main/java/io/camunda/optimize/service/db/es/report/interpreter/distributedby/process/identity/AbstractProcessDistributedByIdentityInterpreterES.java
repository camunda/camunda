/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.identity;

import static io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES.createInclusiveFlowNodeIdFilterQuery;
import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.NamedValue;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.AbstractProcessDistributedByInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.identity.ProcessDistributedByIdentityInterpreter;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.identity.ProcessDistributedByIdentityInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractProcessDistributedByIdentityInterpreterES
    extends AbstractProcessDistributedByInterpreterES {
  private static final String DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION = "identity";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "userTasksFilterAggregation";

  protected abstract ConfigurationService getConfigurationService();

  protected abstract ProcessDistributedByIdentityInterpreterHelper getHelper();

  protected abstract DefinitionService getDefinitionService();

  protected abstract String getIdentityField();

  protected abstract IdentityType getIdentityType();

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQuery baseQuery) {
    final TermsAggregation.Builder builder = new TermsAggregation.Builder();
    builder
        .size(getConfigurationService().getElasticSearchConfiguration().getAggregationBucketLimit())
        .order(NamedValue.of("_key", SortOrder.Asc))
        .field(FLOW_NODE_INSTANCES + "." + getIdentityField())
        .missing(ProcessDistributedByIdentityInterpreter.DISTRIBUTE_BY_IDENTITY_MISSING_KEY);
    final Aggregation.Builder.ContainerBuilder identityTermsAggregation =
        new Aggregation.Builder().terms(builder.build());
    getViewInterpreter()
        .createAggregations(context)
        .forEach((k, v) -> identityTermsAggregation.aggregations(k, v.build()));
    // it's possible to do report evaluations over several definitions versions.
    // However, only the most recent
    // one is used to decide which user tasks should be taken into account. To make sure
    // that we only fetch
    // assignees related to this definition version we filter for userTasks that only
    // occur in the latest version.
    final Aggregation.Builder.ContainerBuilder ag =
        new Aggregation.Builder()
            .filter(
                f ->
                    f.bool(
                        createInclusiveFlowNodeIdFilterQuery(
                                context.getReportData(),
                                getHelper().getUserTaskIds(context.getReportData()),
                                context.getFilterContext(),
                                getDefinitionService())
                            .build()))
            .aggregations(
                DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION, identityTermsAggregation.build());
    return Map.of(FILTERED_USER_TASKS_AGGREGATION, ag);
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final FilterAggregate onlyIdentitiesRelatedToTheLatestDefinitionVersion =
        aggregations.get(FILTERED_USER_TASKS_AGGREGATION).filter();
    final StringTermsAggregate byIdentityAggregations =
        onlyIdentitiesRelatedToTheLatestDefinitionVersion
            .aggregations()
            .get(DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION)
            .sterms();
    final List<CompositeCommandResult.DistributedByResult> distributedByIdentity =
        new ArrayList<>();

    for (final StringTermsBucket identityBucket : byIdentityAggregations.buckets().array()) {
      final CompositeCommandResult.ViewResult viewResult =
          getViewInterpreter().retrieveResult(response, identityBucket.aggregations(), context);

      final String key = identityBucket.key().stringValue();
      if (ProcessDistributedByIdentityInterpreter.DISTRIBUTE_BY_IDENTITY_MISSING_KEY.equals(key)) {
        for (final CompositeCommandResult.ViewMeasure viewMeasure : viewResult.getViewMeasures()) {
          final AggregationDto aggTypeDto = viewMeasure.getAggregationType();
          if (aggTypeDto != null
              && aggTypeDto.getType() == AggregationType.SUM
              && (viewMeasure.getValue() != null && viewMeasure.getValue() == 0)) {
            viewMeasure.setValue(null);
          }
        }
      }

      distributedByIdentity.add(
          createDistributedByResult(
              key, getHelper().resolveIdentityName(key, this::getIdentityType), viewResult));
    }

    getHelper()
        .addEmptyMissingDistributedByResults(
            distributedByIdentity, context, () -> getViewInterpreter().createEmptyResult(context));

    return distributedByIdentity;
  }

  @Override
  public void enrichContextWithAllExpectedDistributedByKeys(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Map<String, Aggregate> aggregations) {
    final FilterAggregate onlyIdentitiesRelatedToTheLatestDefinitionVersion =
        aggregations.get(FILTERED_USER_TASKS_AGGREGATION).filter();
    final StringTermsAggregate allIdentityAggregation =
        onlyIdentitiesRelatedToTheLatestDefinitionVersion
            .aggregations()
            .get(DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION)
            .sterms();
    final Map<String, String> allDistributedByIdentityKeys =
        allIdentityAggregation.buckets().array().stream()
            .map(v -> v.key().stringValue())
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    key -> getHelper().resolveIdentityName(key, this::getIdentityType)));
    context.setAllDistributedByKeysAndLabels(allDistributedByIdentityKeys);
  }
}
