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

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.AssigneeCandidateGroupService;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.db.es.report.interpreter.distributedby.process.AbstractProcessDistributedByInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

public abstract class AbstractProcessDistributedByIdentityInterpreterES
    extends AbstractProcessDistributedByInterpreterES {
  public static final String DISTRIBUTE_BY_IDENTITY_MISSING_KEY = "unassignedUserTasks___";
  private static final String DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION = "identity";
  // temporary GROUP_BY_IDENTITY_MISSING_KEY to ensure no overlap between this label and userTask
  // names
  private static final String FILTERED_USER_TASKS_AGGREGATION = "userTasksFilterAggregation";

  protected abstract ConfigurationService getConfigurationService();

  protected abstract LocalizationService getLocalizationService();

  protected abstract DefinitionService getDefinitionService();

  protected abstract AssigneeCandidateGroupService getAssigneeCandidateGroupService();

  protected abstract String getIdentityField();

  protected abstract IdentityType getIdentityType();

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final QueryBuilder baseQueryBuilder) {
    final TermsAggregationBuilder identityTermsAggregation =
        AggregationBuilders.terms(DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION)
            .size(
                getConfigurationService()
                    .getElasticSearchConfiguration()
                    .getAggregationBucketLimit())
            .order(BucketOrder.key(true))
            .field(FLOW_NODE_INSTANCES + "." + getIdentityField())
            .missing(DISTRIBUTE_BY_IDENTITY_MISSING_KEY);
    getViewInterpreter()
        .createAggregations(context)
        .forEach(identityTermsAggregation::subAggregation);
    return Collections.singletonList(
        AggregationBuilders.filter(
                // it's possible to do report evaluations over several definitions versions.
                // However, only the most recent
                // one is used to decide which user tasks should be taken into account. To make sure
                // that we only fetch
                // assignees related to this definition version we filter for userTasks that only
                // occur in the latest version.
                FILTERED_USER_TASKS_AGGREGATION,
                createInclusiveFlowNodeIdFilterQuery(
                    context.getReportData(),
                    getUserTaskIds(context.getReportData()),
                    context.getFilterContext(),
                    getDefinitionService()))
            .subAggregation(identityTermsAggregation));
  }

  @Override
  public List<CompositeCommandResult.DistributedByResult> retrieveResult(
      final SearchResponse response,
      final Aggregations aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Filter onlyIdentitiesRelatedToTheLatestDefinitionVersion =
        aggregations.get(FILTERED_USER_TASKS_AGGREGATION);
    final Terms byIdentityAggregations =
        onlyIdentitiesRelatedToTheLatestDefinitionVersion
            .getAggregations()
            .get(DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION);
    List<CompositeCommandResult.DistributedByResult> distributedByIdentity = new ArrayList<>();

    for (Terms.Bucket identityBucket : byIdentityAggregations.getBuckets()) {
      CompositeCommandResult.ViewResult viewResult =
          getViewInterpreter().retrieveResult(response, identityBucket.getAggregations(), context);

      final String key = identityBucket.getKeyAsString();
      if (DISTRIBUTE_BY_IDENTITY_MISSING_KEY.equals(key)) {
        for (CompositeCommandResult.ViewMeasure viewMeasure : viewResult.getViewMeasures()) {
          final AggregationDto aggTypeDto = viewMeasure.getAggregationType();
          if (aggTypeDto != null
              && aggTypeDto.getType() == AggregationType.SUM
              && (viewMeasure.getValue() != null && viewMeasure.getValue() == 0)) {
            viewMeasure.setValue(null);
          }
        }
      }

      distributedByIdentity.add(
          createDistributedByResult(key, resolveIdentityName(key), viewResult));
    }

    addEmptyMissingDistributedByResults(distributedByIdentity, context);

    return distributedByIdentity;
  }

  @Override
  public void enrichContextWithAllExpectedDistributedByKeys(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context,
      final Aggregations aggregations) {
    final Filter onlyIdentitiesRelatedToTheLatestDefinitionVersion =
        aggregations.get(FILTERED_USER_TASKS_AGGREGATION);
    final Terms allIdentityAggregation =
        onlyIdentitiesRelatedToTheLatestDefinitionVersion
            .getAggregations()
            .get(DISTRIBUTE_BY_IDENTITY_TERMS_AGGREGATION);
    final Map<String, String> allDistributedByIdentityKeys =
        allIdentityAggregation.getBuckets().stream()
            .map(MultiBucketsAggregation.Bucket::getKeyAsString)
            .collect(Collectors.toMap(Function.identity(), this::resolveIdentityName));
    context.setAllDistributedByKeysAndLabels(allDistributedByIdentityKeys);
  }

  private Set<String> getUserTaskIds(final ProcessReportDataDto reportData) {
    return getDefinitionService()
        .extractUserTaskIdAndNames(
            reportData.getDefinitions().stream()
                .map(
                    definitionDto ->
                        getDefinitionService()
                            .getDefinition(
                                DefinitionType.PROCESS,
                                definitionDto.getKey(),
                                definitionDto.getVersions(),
                                definitionDto.getTenantIds()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(ProcessDefinitionOptimizeDto.class::cast)
                .toList())
        .keySet();
  }

  private String resolveIdentityName(final String key) {
    if (DISTRIBUTE_BY_IDENTITY_MISSING_KEY.equals(key)) {
      return getLocalizationService().getDefaultLocaleMessageForMissingAssigneeLabel();
    }
    return getAssigneeCandidateGroupService()
        .getIdentityByIdAndType(key, getIdentityType())
        .map(IdentityWithMetadataResponseDto::getName)
        .orElse(key);
  }

  private void addEmptyMissingDistributedByResults(
      List<CompositeCommandResult.DistributedByResult> distributedByIdentityResultList,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    context.getAllDistributedByKeysAndLabels().entrySet().stream()
        .filter(
            entry ->
                distributedByIdentityResultList.stream()
                    .noneMatch(
                        distributedByResult -> distributedByResult.getKey().equals(entry.getKey())))
        .map(
            entry ->
                createDistributedByResult(
                    entry.getKey(),
                    entry.getValue(),
                    getViewInterpreter().createEmptyResult(context)))
        .forEach(distributedByIdentityResultList::add);
  }
}
