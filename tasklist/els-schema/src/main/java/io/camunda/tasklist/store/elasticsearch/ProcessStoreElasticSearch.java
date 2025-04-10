/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregation;
import org.elasticsearch.search.aggregations.bucket.composite.CompositeAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.composite.TermsValuesSourceBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.collapse.CollapseBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessStoreElasticSearch implements ProcessStore {

  private static final Boolean CASE_INSENSITIVE = true;
  private static final String BPMN_PROCESS_ID_TENANT_ID_AGG_NAME = "bpmnProcessId_tenantId_buckets";
  private static final String TOP_HITS_AGG_NAME = "top_hit_doc";
  private static final String DEFINITION_ID_TERMS_SOURCE_NAME = "group_by_definition_id";
  private static final String TENANT_ID_TERMS_SOURCE_NAME = "group_by_tenant_id";
  private static final String MAX_VERSION_DOCUMENTS_AGG_NAME = "max_version_docs";
  private static final String STARTED_BY_FORM_FILTERED_DOCS = "started_by_form_docs";

  @Autowired
  @Qualifier("tasklistProcessIndex")
  private ProcessIndex processIndex;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private SecurityConfiguration securityConfiguration;

  @Autowired private TenantAwareElasticsearchClient tenantAwareClient;

  @Override
  public ProcessEntity getProcessByProcessDefinitionKey(final String processDefinitionKey) {
    final QueryBuilder qb = QueryBuilders.termQuery(ProcessIndex.KEY, processDefinitionKey);

    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(qb)
                    .collapse(new CollapseBuilder(ProcessIndex.KEY))
                    .sort(SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC))
                    .size(1));
    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value > 0) {
        final ProcessEntity processEntity =
            fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
        return processEntity;
      } else {
        throw new NotFoundException(
            String.format("Process with key %s not found", processDefinitionKey));
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  /** Gets the process by id. */
  @Override
  public ProcessEntity getProcessByBpmnProcessId(final String bpmnProcessId) {
    return getProcessByBpmnProcessId(bpmnProcessId, null);
  }

  @Override
  public ProcessEntity getProcessByBpmnProcessId(
      final String bpmnProcessId, final String tenantId) {
    final QueryBuilder qb;
    if (securityConfiguration.getMultiTenancy().isEnabled() && StringUtils.isNotBlank(tenantId)) {
      qb =
          ElasticsearchUtil.joinWithAnd(
              QueryBuilders.termQuery(ProcessIndex.BPMN_PROCESS_ID, bpmnProcessId),
              QueryBuilders.termQuery(ProcessIndex.TENANT_ID, tenantId));
    } else {
      qb = QueryBuilders.termQuery(ProcessIndex.BPMN_PROCESS_ID, bpmnProcessId);
    }

    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(qb)
                    .collapse(new CollapseBuilder(ProcessIndex.BPMN_PROCESS_ID))
                    .sort(SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC))
                    .size(1));
    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value > 0) {
        final ProcessEntity processEntity =
            fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
        return processEntity;
      } else {
        throw new NotFoundException(
            String.format("Could not find process with id '%s'.", bpmnProcessId));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public ProcessEntity getProcess(final String processId) {
    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(QueryBuilders.termQuery(ProcessIndex.KEY, processId)));

    try {
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new TasklistRuntimeException(
            String.format("Could not find unique process with id '%s'.", processId));
      } else {
        throw new NotFoundException(
            String.format("Could not find process with id '%s'.", processId));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<ProcessEntity> getProcesses(
      final List<String> processDefinitions, final String tenantId, final Boolean isStartedByForm) {
    final QueryBuilder qb;

    if (securityConfiguration.getAuthorizations().isEnabled()) {
      if (processDefinitions.isEmpty()) {
        return new ArrayList<>();
      }

      if (processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        qb =
            QueryBuilders.boolQuery()
                .must(QueryBuilders.existsQuery(ProcessIndex.BPMN_PROCESS_ID))
                .mustNot(QueryBuilders.termQuery(ProcessIndex.BPMN_PROCESS_ID, ""));
      } else {

        qb =
            QueryBuilders.boolQuery()
                .must(QueryBuilders.termsQuery(ProcessIndex.BPMN_PROCESS_ID, processDefinitions))
                .must(QueryBuilders.existsQuery(ProcessIndex.BPMN_PROCESS_ID))
                .mustNot(QueryBuilders.termQuery(ProcessIndex.BPMN_PROCESS_ID, ""));
      }

    } else {
      qb =
          QueryBuilders.boolQuery()
              .must(QueryBuilders.existsQuery(ProcessIndex.BPMN_PROCESS_ID))
              .mustNot(QueryBuilders.termQuery(ProcessIndex.BPMN_PROCESS_ID, ""));
    }

    final QueryBuilder finalQuery = enhanceQueryByTenantIdCheck(qb, tenantId);
    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(finalQuery, isStartedByForm);
  }

  @Override
  public List<ProcessEntity> getProcesses(
      final String search,
      final List<String> processDefinitions,
      final String tenantId,
      final Boolean isStartedByForm) {

    if (StringUtils.isBlank(search)) {
      return getProcesses(processDefinitions, tenantId, isStartedByForm);
    }

    final String regexSearch = String.format(".*%s.*", search);
    BoolQueryBuilder qb =
        QueryBuilders.boolQuery()
            .should(QueryBuilders.termQuery(ProcessIndex.ID, search))
            .should(
                QueryBuilders.regexpQuery(ProcessIndex.NAME, regexSearch)
                    .caseInsensitive(CASE_INSENSITIVE))
            .should(
                QueryBuilders.regexpQuery(ProcessIndex.BPMN_PROCESS_ID, regexSearch)
                    .caseInsensitive(CASE_INSENSITIVE))
            .must(QueryBuilders.existsQuery(ProcessIndex.BPMN_PROCESS_ID))
            .mustNot(QueryBuilders.termQuery(ProcessIndex.BPMN_PROCESS_ID, ""))
            .minimumShouldMatch(1);

    if (securityConfiguration.getAuthorizations().isEnabled()) {
      if (processDefinitions.isEmpty()) {
        return new ArrayList<>();
      }
      if (!processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        qb = qb.must(QueryBuilders.termsQuery(ProcessIndex.BPMN_PROCESS_ID, processDefinitions));
      }
    }
    final QueryBuilder finalQuery = enhanceQueryByTenantIdCheck(qb, tenantId);
    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(finalQuery, isStartedByForm);
  }

  @Override
  public List<ProcessEntity> getProcessesStartedByForm() {
    final QueryBuilder qb;

    qb =
        QueryBuilders.boolQuery()
            .must(QueryBuilders.existsQuery(ProcessIndex.BPMN_PROCESS_ID))
            .mustNot(QueryBuilders.termQuery(ProcessIndex.BPMN_PROCESS_ID, ""));

    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(qb, true);
  }

  private ProcessEntity fromSearchHit(final String processString) {
    return ElasticsearchUtil.fromSearchHit(processString, objectMapper, ProcessEntity.class);
  }

  private QueryBuilder enhanceQueryByTenantIdCheck(final QueryBuilder qb, final String tenantId) {
    if (securityConfiguration.getMultiTenancy().isEnabled() && StringUtils.isNotBlank(tenantId)) {
      return ElasticsearchUtil.joinWithAnd(
          QueryBuilders.termQuery(ProcessIndex.TENANT_ID, tenantId), qb);
    }

    return qb;
  }

  public List<ProcessEntity> getProcessEntityUniqueByProcessDefinitionIdAndTenantId(
      final QueryBuilder qb, final Boolean isStartedByForm) {

    final CompositeAggregationBuilder processDefinitionAndTenantBucket =
        AggregationBuilders.composite(
                BPMN_PROCESS_ID_TENANT_ID_AGG_NAME,
                List.of(
                    new TermsValuesSourceBuilder(DEFINITION_ID_TERMS_SOURCE_NAME)
                        .field(ProcessIndex.BPMN_PROCESS_ID),
                    new TermsValuesSourceBuilder(TENANT_ID_TERMS_SOURCE_NAME)
                        .field(ProcessIndex.TENANT_ID)))
            .size(ElasticsearchUtil.QUERY_MAX_SIZE);

    final AggregationBuilder maxVersionDocsAggregate =
        AggregationBuilders.terms(MAX_VERSION_DOCUMENTS_AGG_NAME)
            .field(ProcessIndex.VERSION)
            .order(BucketOrder.key(false))
            .size(1);

    final AggregationBuilder topHitsAggregate =
        AggregationBuilders.topHits(TOP_HITS_AGG_NAME)
            .sort(SortBuilders.fieldSort(ProcessIndex.VERSION).order(SortOrder.DESC))
            .size(1);

    final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(qb).size(0);
    if (isStartedByForm == null) {
      sourceBuilder.aggregation(
          processDefinitionAndTenantBucket.subAggregation(
              maxVersionDocsAggregate.subAggregation(topHitsAggregate)));
    } else {
      sourceBuilder.aggregation(
          processDefinitionAndTenantBucket.subAggregation(
              maxVersionDocsAggregate.subAggregation(
                  startedByFormAggregateFilter(isStartedByForm).subAggregation(topHitsAggregate))));
    }

    final SearchRequest searchRequest =
        new SearchRequest(processIndex.getAlias()).source(sourceBuilder);

    final SearchResponse response;
    try {
      response = tenantAwareClient.search(searchRequest);
      final CompositeAggregation compositeAgg =
          response.getAggregations().get(BPMN_PROCESS_ID_TENANT_ID_AGG_NAME);
      final Set<SearchHit> hits =
          isStartedByForm != null
              ? getFilteredAggregateSearchHits(compositeAgg)
              : getAggregateSearchHits(compositeAgg);
      return hits.stream()
          .map(
              hit ->
                  ElasticsearchUtil.fromSearchHit(
                      hit.getSourceAsString(), objectMapper, ProcessEntity.class))
          .toList();

    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private FilterAggregationBuilder startedByFormAggregateFilter(final boolean isStartedByForm) {
    return isStartedByForm
        ? AggregationBuilders.filter(
            STARTED_BY_FORM_FILTERED_DOCS,
            QueryBuilders.boolQuery()
                .should(QueryBuilders.existsQuery(ProcessIndex.FORM_KEY))
                .should(QueryBuilders.existsQuery(ProcessIndex.FORM_ID))
                .minimumShouldMatch(1))
        : AggregationBuilders.filter(
            STARTED_BY_FORM_FILTERED_DOCS,
            QueryBuilders.boolQuery()
                .mustNot(QueryBuilders.existsQuery(ProcessIndex.FORM_KEY))
                .mustNot(QueryBuilders.existsQuery(ProcessIndex.FORM_ID))
                .minimumShouldMatch(1));
  }

  private Set<SearchHit> getFilteredAggregateSearchHits(final CompositeAggregation composite) {
    return composite.getBuckets().stream()
        .flatMap(
            bucket ->
                ((ParsedTerms) bucket.getAggregations().asMap().get(MAX_VERSION_DOCUMENTS_AGG_NAME))
                    .getBuckets().stream()
                        .flatMap(
                            versionBucket -> {
                              final var startedByFormDocs =
                                  ((Filter)
                                          versionBucket
                                              .getAggregations()
                                              .get(STARTED_BY_FORM_FILTERED_DOCS))
                                      .getAggregations();
                              return Arrays.stream(
                                  ((TopHits) startedByFormDocs.get(TOP_HITS_AGG_NAME))
                                      .getHits()
                                      .getHits());
                            }))
        .collect(Collectors.toSet());
  }

  private Set<SearchHit> getAggregateSearchHits(final CompositeAggregation composite) {
    return composite.getBuckets().stream()
        .flatMap(
            bucket ->
                ((ParsedTerms) bucket.getAggregations().get(MAX_VERSION_DOCUMENTS_AGG_NAME))
                    .getBuckets().stream()
                        .flatMap(
                            versionBucket ->
                                Arrays.stream(
                                    ((TopHits)
                                            versionBucket.getAggregations().get(TOP_HITS_AGG_NAME))
                                        .getHits()
                                        .getHits())))
        .collect(Collectors.toSet());
  }
}
