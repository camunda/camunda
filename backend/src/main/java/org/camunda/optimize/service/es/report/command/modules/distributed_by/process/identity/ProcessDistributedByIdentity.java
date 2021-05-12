/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.AssigneeCandidateGroupService;
import org.camunda.optimize.service.DefinitionService;
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
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.filter.util.modelelement.UserTaskFilterQueryUtil.createUserTaskIdentityAggregationFilter;
import static org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult.createDistributedByResult;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

@RequiredArgsConstructor
public abstract class ProcessDistributedByIdentity extends ProcessDistributedByPart {
  public static final String DISTRIBUTE_BY_IDENTITY_MISSING_KEY = "unassignedUserTasks___";
  private static final String DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION = "identity";
  // temporary GROUP_BY_IDENTITY_MISSING_KEY to ensure no overlap between this label and userTask names
  private static final String FILTERED_USER_TASKS_AGGREGATION = "userTasksFilterAggregation";

  private final ConfigurationService configurationService;
  private final LocalizationService localizationService;
  private final DefinitionService definitionService;
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
    final TermsAggregationBuilder identityTermsAggregation = AggregationBuilders
      .terms(DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .order(BucketOrder.key(true))
      .field(FLOW_NODE_INSTANCES + "." + getIdentityField())
      .missing(DISTRIBUTE_BY_IDENTITY_MISSING_KEY);
    viewPart.createAggregations(context).forEach(identityTermsAggregation::subAggregation);
    return Collections.singletonList(
      AggregationBuilders.filter(
        FILTERED_USER_TASKS_AGGREGATION,
        createUserTaskIdentityAggregationFilter(context.getReportData(), getUserTaskIds(context.getReportData()))
      ).subAggregation(identityTermsAggregation)
    );
  }

  private Set<String> getUserTaskIds(final ProcessReportDataDto reportData) {
    return definitionService
      .getDefinition(
        DefinitionType.PROCESS,
        reportData.getDefinitionKey(),
        reportData.getDefinitionVersions(),
        reportData.getTenantIds()
      )
      .map(def -> ((ProcessDefinitionOptimizeDto) def).getUserTaskNames())
      .map(Map::keySet)
      .orElse(Collections.emptySet());
  }

  protected abstract String getIdentityField();

  protected abstract IdentityType getIdentityType();

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(final SearchResponse response,
                                                                         final Aggregations aggregations,
                                                                         final ExecutionContext<ProcessReportDataDto> context) {
    final Filter onlyIdentitiesRelatedToTheLatestDefinitionVersion =
      aggregations.get(FILTERED_USER_TASKS_AGGREGATION);
    final Terms byIdentityAggregations =
      onlyIdentitiesRelatedToTheLatestDefinitionVersion.getAggregations()
        .get(DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION);
    List<CompositeCommandResult.DistributedByResult> distributedByIdentity = new ArrayList<>();

    for (Terms.Bucket identityBucket : byIdentityAggregations.getBuckets()) {
      final CompositeCommandResult.ViewResult viewResult = viewPart.retrieveResult(
        response, identityBucket.getAggregations(), context
      );
      final String key = identityBucket.getKeyAsString();
      distributedByIdentity.add(createDistributedByResult(key, resolveIdentityName(key), viewResult));
    }

    addEmptyMissingDistributedByResults(distributedByIdentity, context);

    return distributedByIdentity;
  }

  private String resolveIdentityName(final String key) {
    if (DISTRIBUTE_BY_IDENTITY_MISSING_KEY.equals(key)) {
      return localizationService.getDefaultLocaleMessageForMissingAssigneeLabel();
    }
    return assigneeCandidateGroupService.getIdentityByIdAndType(key, getIdentityType())
      .map(IdentityWithMetadataResponseDto::getName)
      .orElse(key);
  }

  private void addEmptyMissingDistributedByResults(
    List<CompositeCommandResult.DistributedByResult> distributedByIdentityResultList,
    final ExecutionContext<ProcessReportDataDto> context) {
    context.getAllDistributedByKeysAndLabels()
      .entrySet()
      .stream()
      .filter(entry -> distributedByIdentityResultList.stream()
        .noneMatch(distributedByResult -> distributedByResult.getKey().equals(entry.getKey())))
      .map(entry -> createDistributedByResult(entry.getKey(), entry.getValue(), viewPart.createEmptyResult(context)))
      .forEach(distributedByIdentityResultList::add);
  }

  @Override
  public void enrichContextWithAllExpectedDistributedByKeys(final ExecutionContext<ProcessReportDataDto> context,
                                                            final Aggregations aggregations) {
    final Filter onlyIdentitiesRelatedToTheLatestDefinitionVersion =
      aggregations.get(FILTERED_USER_TASKS_AGGREGATION);
    final Terms allIdentityAggregation =
      onlyIdentitiesRelatedToTheLatestDefinitionVersion.getAggregations()
        .get(DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION);
    final Map<String, String> allDistributedByIdentityKeys = allIdentityAggregation.getBuckets()
      .stream()
      .map(MultiBucketsAggregation.Bucket::getKeyAsString)
      .collect(Collectors.toMap(Function.identity(), this::resolveIdentityName));
    context.setAllDistributedByKeysAndLabels(allDistributedByIdentityKeys);
  }

}
