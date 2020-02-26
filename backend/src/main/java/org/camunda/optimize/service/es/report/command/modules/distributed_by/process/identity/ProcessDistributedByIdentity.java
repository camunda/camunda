/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.USER_TASKS;

@RequiredArgsConstructor
public abstract class ProcessDistributedByIdentity extends ProcessDistributedByPart {

  private final ConfigurationService configurationService;
  private final LocalizationService localizationService;

  private static final String DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION = "identity";
  // temporary GROUP_BY_IDENTITY_MISSING_KEY to ensure no overlap between this label and userTask names
  private static final String DISTRIBUTE_BY_IDENTITY_MISSING_KEY = "unassignedUserTasks___";

  @Override
  public AggregationBuilder createAggregation(final ExecutionContext<ProcessReportDataDto> context) {
    return AggregationBuilders
      .terms(DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .order(BucketOrder.key(true))
      .field(USER_TASKS + "." + getIdentityField())
      .missing(DISTRIBUTE_BY_IDENTITY_MISSING_KEY)
      .subAggregation(viewPart.createAggregation(context));
  }

  protected abstract String getIdentityField();

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(final SearchResponse response,
                                                                         final Aggregations aggregations,
                                                                         final ExecutionContext<ProcessReportDataDto> context) {
    final Terms byIdentityAggregations = aggregations.get(DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION);
    List<CompositeCommandResult.DistributedByResult> distributedByIdentity = new ArrayList<>();

    for (Terms.Bucket identityBucket : byIdentityAggregations.getBuckets()) {
      final CompositeCommandResult.ViewResult viewResult = viewPart.retrieveResult(
        response,
        identityBucket.getAggregations(),
        context
      );
      final String key = identityBucket.getKeyAsString().equals(DISTRIBUTE_BY_IDENTITY_MISSING_KEY)
        ? localizationService.getDefaultLocaleMessageForMissingAssigneeLabel()
        : identityBucket.getKeyAsString();
      distributedByIdentity.add(createDistributedByResult(key, null, viewResult));
    }

    addEmptyMissingDistributedByResults(distributedByIdentity, context);

    return distributedByIdentity;
  }

  public void addEmptyMissingDistributedByResults(
    List<CompositeCommandResult.DistributedByResult> distributedByIdentityResultList,
    final ExecutionContext<ProcessReportDataDto> context) {
    context.getAllDistributedByKeys().stream()
      .filter(key -> distributedByIdentityResultList.stream()
        .noneMatch(distributedByResult -> distributedByResult.getKey().equals(key)))
      .map(CompositeCommandResult.DistributedByResult::createResultWithEmptyValue)
      .forEach(distributedByIdentityResultList::add);
  }

  @Override
  public void enrichContextWithAllExpectedDistributedByKeys(
    final ExecutionContext<ProcessReportDataDto> context,
    final Aggregations aggregations) {
    final Terms allIdentityAggregation = aggregations.get(DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION);
    final Set<String> allDistributedByIdentityKeys = allIdentityAggregation.getBuckets()
      .stream()
      .map(identityBucket -> identityBucket.getKeyAsString().equals(DISTRIBUTE_BY_IDENTITY_MISSING_KEY)
        ? localizationService.getDefaultLocaleMessageForMissingAssigneeLabel()
        : identityBucket.getKeyAsString())
      .collect(toSet());
    context.setAllDistributedByKeys(allDistributedByIdentityKeys);
  }
}
