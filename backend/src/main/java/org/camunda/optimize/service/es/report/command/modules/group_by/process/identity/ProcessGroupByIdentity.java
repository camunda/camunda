/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.group_by.process.identity;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.service.AssigneeCandidateGroupService;
import org.camunda.optimize.service.DefinitionService;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.es.filter.util.ModelElementFilterQueryUtil;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.identity.ProcessDistributedByIdentity;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.ProcessGroupByPart;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.DistributedByResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.GroupByResult;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.filter.util.ModelElementFilterQueryUtil.createUserTaskFlowNodeTypeFilter;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.nested;

@RequiredArgsConstructor
public abstract class ProcessGroupByIdentity extends ProcessGroupByPart {

  private static final String GROUP_BY_IDENTITY_TERMS_AGGREGATION = "identities";
  private static final String FLOW_NODES_AGGREGATION = "flowNodeInstances";
  private static final String USER_TASKS_AGGREGATION = "userTasks";
  private static final String FILTERED_USER_TASKS_AGGREGATION = "filteredUserTasks";
  // temporary GROUP_BY_IDENTITY_MISSING_KEY to ensure no overlap between this label and userTask names
  private static final String GROUP_BY_IDENTITY_MISSING_KEY = "unassignedUserTasks___";

  protected final ConfigurationService configurationService;
  protected final LocalizationService localizationService;
  protected final DefinitionService definitionService;
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;

  @Override
  public List<AggregationBuilder> createAggregation(final SearchSourceBuilder searchSourceBuilder,
                                                    final ExecutionContext<ProcessReportDataDto> context) {
    final TermsAggregationBuilder identityTermsAggregation = AggregationBuilders
      .terms(GROUP_BY_IDENTITY_TERMS_AGGREGATION)
      .size(configurationService.getEsAggregationBucketLimit())
      .order(BucketOrder.key(true))
      .field(FLOW_NODE_INSTANCES + "." + getIdentityField())
      .missing(GROUP_BY_IDENTITY_MISSING_KEY);
    distributedByPart.createAggregations(context).forEach(identityTermsAggregation::subAggregation);
    final NestedAggregationBuilder groupByIdentityAggregation = nested(FLOW_NODES_AGGREGATION, FLOW_NODE_INSTANCES)
      .subAggregation(
        filter(USER_TASKS_AGGREGATION, createUserTaskFlowNodeTypeFilter())
          .subAggregation(
            filter(
              // it's possible to do report evaluations over several definitions versions. However, only the most recent
              // one is used to decide which user tasks should be taken into account. To make sure that we only fetch
              // assignees related to this definition version we filter for userTasks that only occur in the latest
              // version.
              FILTERED_USER_TASKS_AGGREGATION,
              ModelElementFilterQueryUtil.createInclusiveFlowNodeIdFilterQuery(
                context.getReportData(),
                getUserTaskIds(context.getReportData()),
                context.getFilterContext(),
                definitionService
              )
            ).subAggregation(identityTermsAggregation)));

    return Collections.singletonList(groupByIdentityAggregation);
  }

  public void addQueryResult(final CompositeCommandResult compositeCommandResult,
                             final SearchResponse response,
                             final ExecutionContext<ProcessReportDataDto> context) {
    final Aggregations aggregations = response.getAggregations();
    final Nested flowNodes = aggregations.get(FLOW_NODES_AGGREGATION);
    final Filter userTasks = flowNodes.getAggregations().get(USER_TASKS_AGGREGATION);
    final Filter filteredUserTasks = userTasks.getAggregations().get(FILTERED_USER_TASKS_AGGREGATION);
    final List<GroupByResult> groupedData = getByIdentityAggregationResults(response, filteredUserTasks, context);

    compositeCommandResult.setGroups(groupedData);
    compositeCommandResult.setGroupBySorting(
      context.getReportConfiguration()
        .getSorting()
        .orElseGet(() -> new ReportSortingDto(ReportSortingDto.SORT_BY_LABEL, SortOrder.ASC))
    );
  }

  protected abstract String getIdentityField();

  protected abstract IdentityType getIdentityType();

  private Set<String> getUserTaskIds(final ProcessReportDataDto reportData) {
    return definitionService.extractUserTaskIdAndNames(
      reportData.getDefinitions().stream()
        .map(definitionDto -> definitionService.getDefinition(
          DefinitionType.PROCESS, definitionDto.getKey(), definitionDto.getVersions(), definitionDto.getTenantIds()
        ))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(ProcessDefinitionOptimizeDto.class::cast)
        .collect(Collectors.toList())
    ).keySet();
  }

  private List<GroupByResult> getByIdentityAggregationResults(final SearchResponse response,
                                                              final Filter filteredUserTasks,
                                                              final ExecutionContext<ProcessReportDataDto> context) {
    final Terms byIdentityAggregation = filteredUserTasks.getAggregations().get(GROUP_BY_IDENTITY_TERMS_AGGREGATION);
    final List<GroupByResult> groupedData = new ArrayList<>();
    for (Terms.Bucket identityBucket : byIdentityAggregation.getBuckets()) {
      final String key = identityBucket.getKeyAsString();
      List<DistributedByResult> distributedByResults =
        distributedByPart.retrieveResult(response, identityBucket.getAggregations(), context);

      if (GROUP_BY_IDENTITY_MISSING_KEY.equals(key)) {
        distributedByResults.forEach(result -> result.getViewResult().getViewMeasures().forEach(measure -> {
          if (AggregationType.SUM.equals(measure.getAggregationType()) && (measure.getValue() != null && measure.getValue() == 0)) {
            measure.setValue(null);
          }
        }));
      }

      // ensure missing identity bucket is excluded if its empty
      final boolean resultIsEmpty = distributedByResults.isEmpty() || distributedByResults.stream()
        .map(DistributedByResult::getViewResult)
        .map(CompositeCommandResult.ViewResult::getViewMeasures)
        .flatMap(Collection::stream)
        .allMatch(viewMeasure -> viewMeasure.getValue() == null || viewMeasure.getValue() == 0.0);
      if (resultIsEmpty) {
        continue;
      }

      groupedData.add(GroupByResult.createGroupByResult(key, resolveIdentityName(key), distributedByResults));
    }
    return groupedData;
  }

  private String resolveIdentityName(final String key) {
    if (ProcessDistributedByIdentity.DISTRIBUTE_BY_IDENTITY_MISSING_KEY.equals(key)) {
      return localizationService.getDefaultLocaleMessageForMissingAssigneeLabel();
    }
    return assigneeCandidateGroupService.getIdentityByIdAndType(key, getIdentityType())
      .map(IdentityWithMetadataResponseDto::getName)
      .orElse(key);
  }
}
