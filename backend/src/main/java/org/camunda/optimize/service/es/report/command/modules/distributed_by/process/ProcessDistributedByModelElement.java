/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.ProcessDistributedByDto;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;

@RequiredArgsConstructor
public abstract class ProcessDistributedByModelElement extends ProcessDistributedByPart {

  private static final String MODEL_ELEMENT_ID_TERMS_AGGREGATION = "modelElement";

  private final ConfigurationService configurationService;
  private final DefinitionService definitionService;

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    return AggregationBuilders
      .terms(MODEL_ELEMENT_ID_TERMS_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .order(BucketOrder.key(true))
      .field(getModelElementIdPath())
      .subAggregation(viewPart.createAggregation(context));
  }

  @Override
  public List<DistributedByResult> retrieveResult(final SearchResponse response,
                                                  final Aggregations aggregations,
                                                  final ExecutionContext<ProcessReportDataDto> context) {
    final Terms byModelElementAggregation = aggregations.get(MODEL_ELEMENT_ID_TERMS_AGGREGATION);
    final Map<String, String> modelElementNames = getModelElementNames(context.getReportData());
    final List<DistributedByResult> distributedByModelElements = new ArrayList<>();
    for (Terms.Bucket modelElementBucket : byModelElementAggregation.getBuckets()) {
      final ViewResult viewResult = viewPart.retrieveResult(response, modelElementBucket.getAggregations(), context);
      final String modelElementKey = modelElementBucket.getKeyAsString();
      if (modelElementNames.containsKey(modelElementKey)) {
        String label = modelElementNames.get(modelElementKey);
        distributedByModelElements.add(createDistributedByResult(modelElementKey, label, viewResult));
        modelElementNames.remove(modelElementKey);
      }
    }
    addMissingDistributions(modelElementNames, distributedByModelElements);
    return distributedByModelElements;
  }

  private void addMissingDistributions(final Map<String, String> modelElementNames,
                                       final List<DistributedByResult> distributedByModelElements) {
    // enrich data model elements that haven't been executed, but should still show up in the result
    modelElementNames.keySet().forEach(modelElementKey -> {
      DistributedByResult emptyResult = DistributedByResult.createResultWithEmptyValue(modelElementKey);
      emptyResult.setLabel(modelElementNames.get(modelElementKey));
      distributedByModelElements.add(emptyResult);
    });
  }

  private Map<String, String> getModelElementNames(final ProcessReportDataDto reportData) {
    return definitionService
      .getDefinition(
        DefinitionType.PROCESS,
        reportData.getDefinitionKey(),
        reportData.getDefinitionVersions(),
        reportData.getTenantIds()
      )
      .map(this::extractModelElementNames)
      .orElse(Collections.emptyMap());
  }

  @Override
  protected void addAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    dataForCommandKey.setDistributedBy(getDistributedBy());
  }

  protected abstract String getModelElementIdPath();

  protected abstract Map<String, String> extractModelElementNames(DefinitionOptimizeResponseDto def);

  protected abstract ProcessDistributedByDto getDistributedBy();

}
