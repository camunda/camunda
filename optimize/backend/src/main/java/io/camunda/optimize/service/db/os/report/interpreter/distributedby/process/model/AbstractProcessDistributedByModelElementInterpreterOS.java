/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.model;

import static io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.distributedby.process.AbstractProcessDistributedByInterpreterOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.distributedby.process.model.ProcessDistributedByModelElementInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.StringTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;

public abstract class AbstractProcessDistributedByModelElementInterpreterOS
    extends AbstractProcessDistributedByInterpreterOS {

  private static final String MODEL_ELEMENT_ID_TERMS_AGGREGATION = "modelElement";

  protected abstract ConfigurationService getConfigurationService();

  protected abstract DefinitionService getDefinitionService();

  protected abstract ProcessDistributedByModelElementInterpreterHelper getHelper();

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Query baseQuery) {
    final TermsAggregation termsAggregation =
        new TermsAggregation.Builder()
            .size(
                getConfigurationService().getOpenSearchConfiguration().getAggregationBucketLimit())
            .order(Map.of("_key", SortOrder.Asc))
            .field(getModelElementIdPath())
            .build();
    final Aggregation aggregation =
        new Aggregation.Builder()
            .terms(termsAggregation)
            .aggregations(getViewInterpreter().createAggregations(context))
            .build();

    return Map.of(MODEL_ELEMENT_ID_TERMS_AGGREGATION, aggregation);
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(
      final SearchResponse<RawResult> response,
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
      final String modelElementKey = modelElementBucket.key();
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
