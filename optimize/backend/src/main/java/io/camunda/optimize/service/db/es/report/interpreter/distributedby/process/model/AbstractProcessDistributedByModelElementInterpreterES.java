/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.model;

import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.NamedValue;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.AbstractProcessDistributedByInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.model.ProcessDistributedByModelElementInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractProcessDistributedByModelElementInterpreterES
    extends AbstractProcessDistributedByInterpreterES {
  private static final String MODEL_ELEMENT_ID_TERMS_AGGREGATION = "modelElement";

  protected abstract ConfigurationService getConfigurationService();

  protected abstract DefinitionService getDefinitionService();

  protected abstract ProcessDistributedByModelElementInterpreterHelper getHelper();

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final BoolQuery baseQueryBuilder) {
    final Aggregation.Builder.ContainerBuilder builder =
        new Aggregation.Builder()
            .terms(
                t ->
                    t.size(
                            getConfigurationService()
                                .getElasticSearchConfiguration()
                                .getAggregationBucketLimit())
                        .order(NamedValue.of("_key", SortOrder.Asc))
                        .field(getModelElementIdPath()));

    getViewInterpreter()
        .createAggregations(context)
        .forEach((k, v) -> builder.aggregations(k, v.build()));
    return Map.of(MODEL_ELEMENT_ID_TERMS_AGGREGATION, builder);
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final StringTermsAggregate byModelElementAggregation =
        aggregations.get(MODEL_ELEMENT_ID_TERMS_AGGREGATION).sterms();
    final Map<String, FlowNodeDataDto> modelElementData =
        getHelper().getModelElementData(context.getReportData(), this::extractModelElementData);
    final List<CompositeCommandResult.DistributedByResult> distributedByModelElements =
        new ArrayList<>();
    for (final StringTermsBucket modelElementBucket : byModelElementAggregation.buckets().array()) {
      final CompositeCommandResult.ViewResult viewResult =
          getViewInterpreter().retrieveResult(response, modelElementBucket.aggregations(), context);
      final String modelElementKey = modelElementBucket.key().stringValue();
      if (modelElementData.containsKey(modelElementKey)) {
        final String label = modelElementData.get(modelElementKey).getName();
        distributedByModelElements.add(
            createDistributedByResult(modelElementKey, label, viewResult));
        modelElementData.remove(modelElementKey);
      }
    }
    distributedByModelElements.addAll(
        getHelper().missingDistributions(modelElementData, getViewInterpreter(), context));
    return distributedByModelElements;
  }

  protected abstract String getModelElementIdPath();

  protected abstract Map<String, FlowNodeDataDto> extractModelElementData(
      DefinitionOptimizeResponseDto def);
}
