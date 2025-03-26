/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.AggregationBuilders;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregate;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregation.Builder;
import org.opensearch.client.opensearch._types.aggregations.CompositeAggregationSource;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch._types.aggregations.LongTermsAggregate;
import org.opensearch.client.opensearch._types.aggregations.TermsAggregation;
import org.opensearch.client.opensearch._types.aggregations.TopHitsAggregate;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.FieldCollapse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessStoreOpenSearch implements ProcessStore {

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

  @Autowired private TenantAwareOpenSearchClient tenantAwareClient;

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private SecurityConfiguration securityConfiguration;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public ProcessEntity getProcessByProcessDefinitionKey(final String processDefinitionKey) {
    try {
      final FieldCollapse keyCollapse = new FieldCollapse.Builder().field(ProcessIndex.KEY).build();
      final SortOptions sortOptions =
          new SortOptions.Builder()
              .field(FieldSort.of(f -> f.field(ProcessIndex.VERSION).order(SortOrder.Desc)))
              .build();

      final var searchRequestBuilder =
          new SearchRequest.Builder()
              .index(List.of(processIndex.getAlias()))
              .query(
                  q ->
                      q.term(
                          t ->
                              t.field(ProcessIndex.KEY).value(FieldValue.of(processDefinitionKey))))
              .collapse(keyCollapse)
              .sort(sortOptions)
              .size(1);

      final SearchResponse<ProcessEntity> response =
          tenantAwareClient.search(searchRequestBuilder, ProcessEntity.class);
      if (!response.hits().hits().isEmpty()) {
        return response.hits().hits().getFirst().source();
      } else {
        throw new NotFoundException(
            String.format("Process with key %s not found", processDefinitionKey));
      }
    } catch (final IOException | OpenSearchException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  @Override
  public ProcessEntity getProcessByBpmnProcessId(final String bpmnProcessId) {
    return getProcessByBpmnProcessId(bpmnProcessId, null);
  }

  @Override
  public ProcessEntity getProcessByBpmnProcessId(
      final String bpmnProcessId, final String tenantId) {
    final FieldCollapse keyCollapse =
        new FieldCollapse.Builder().field(ProcessIndex.BPMN_PROCESS_ID).build();
    final SortOptions sortOptions =
        new SortOptions.Builder()
            .field(FieldSort.of(f -> f.field(ProcessIndex.VERSION).order(SortOrder.Desc)))
            .build();

    final Query qb;
    final Query functionQuery =
        new Query.Builder()
            .term(
                term ->
                    term.field(ProcessIndex.BPMN_PROCESS_ID).value(FieldValue.of(bpmnProcessId)))
            .build();
    if (securityConfiguration.getMultiTenancy().isEnabled() && StringUtils.isNotBlank(tenantId)) {
      qb =
          OpenSearchUtil.joinWithAnd(
              new Query.Builder()
                  .term(term -> term.field(ProcessIndex.TENANT_ID).value(FieldValue.of(tenantId)))
                  .build(),
              functionQuery);
    } else {
      qb = functionQuery;
    }

    final SearchRequest.Builder searchRequestBuilder =
        new SearchRequest.Builder()
            .index(List.of(processIndex.getAlias()))
            .query(qb)
            .collapse(keyCollapse)
            .sort(sortOptions)
            .size(1);
    final SearchResponse<ProcessEntity> response;
    try {
      response = tenantAwareClient.search(searchRequestBuilder, ProcessEntity.class);
      if (!response.hits().hits().isEmpty()) {
        return response.hits().hits().getFirst().source();
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

    final SearchResponse<ProcessEntity> response;
    try {
      final var searchRequestBuilder =
          new SearchRequest.Builder()
              .index(List.of(processIndex.getAlias()))
              .query(q -> q.term(t -> t.field(ProcessIndex.KEY).value(FieldValue.of(processId))));
      response = tenantAwareClient.search(searchRequestBuilder, ProcessEntity.class);

      final long totalHits = response.hits().total().value();
      if (totalHits == 1L) {
        return response.hits().hits().getFirst().source();
      } else if (totalHits > 1) {
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

    final Query q;

    if (tasklistProperties.isSelfManaged()) {

      if (processDefinitions.isEmpty()) {
        return new ArrayList<>();
      }

      if (processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        q =
            QueryBuilders.bool()
                .must(t -> t.exists(e -> e.field(ProcessIndex.BPMN_PROCESS_ID)))
                .mustNot(
                    mn ->
                        mn.term(
                            t -> t.field(ProcessIndex.BPMN_PROCESS_ID).value(FieldValue.of(""))))
                .build()
                .toQuery();
      } else {
        q =
            QueryBuilders.bool()
                .must(
                    m ->
                        m.terms(
                            terms ->
                                terms
                                    .field(ProcessIndex.BPMN_PROCESS_ID)
                                    .terms(
                                        v ->
                                            v.value(
                                                processDefinitions.stream()
                                                    .map(FieldValue::of)
                                                    .collect(Collectors.toList())))))
                .must(m -> m.exists(e -> e.field(ProcessIndex.BPMN_PROCESS_ID)))
                .mustNot(
                    mn ->
                        mn.term(
                            t -> t.field(ProcessIndex.BPMN_PROCESS_ID).value(FieldValue.of(""))))
                .build()
                .toQuery();
      }
    } else {
      q =
          QueryBuilders.bool()
              .must(m -> m.exists(e -> e.field(ProcessIndex.BPMN_PROCESS_ID)))
              .mustNot(
                  mn ->
                      mn.term(t -> t.field(ProcessIndex.BPMN_PROCESS_ID).value(FieldValue.of(""))))
              .build()
              .toQuery();
    }

    final Query applyTenantIdFilter = addFilterOnTenantIdIfRequired(q, tenantId);

    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(
        applyTenantIdFilter, isStartedByForm);
  }

  @Override
  public List<ProcessEntity> getProcesses(
      final String search,
      final List<String> processDefinitions,
      final String tenantId,
      final Boolean isStartedByForm) {

    if (search == null || search.isBlank()) {
      return getProcesses(processDefinitions, tenantId, isStartedByForm);
    }

    final String regexSearch = String.format(".*%s.*", search);
    final BoolQuery.Builder query =
        QueryBuilders.bool()
            .should(s -> s.term(t -> t.field(ProcessIndex.ID).value(FieldValue.of(search))))
            .should(
                s ->
                    s.regexp(
                        regex ->
                            regex
                                .field(ProcessIndex.NAME)
                                .caseInsensitive(CASE_INSENSITIVE)
                                .value(regexSearch)))
            .should(
                s ->
                    s.regexp(
                        regex ->
                            regex
                                .field(ProcessIndex.BPMN_PROCESS_ID)
                                .caseInsensitive(CASE_INSENSITIVE)
                                .value(regexSearch)))
            .must(m -> m.exists(e -> e.field(ProcessIndex.BPMN_PROCESS_ID)))
            .mustNot(
                mn -> mn.term(t -> t.field(ProcessIndex.BPMN_PROCESS_ID).value(FieldValue.of(""))))
            .minimumShouldMatch("1");

    if (securityConfiguration.getAuthorizations().isEnabled()) {
      if (processDefinitions.isEmpty()) {
        return new ArrayList<>();
      }
      if (!processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        query.must(
            m ->
                m.terms(
                    terms ->
                        terms
                            .field(ProcessIndex.BPMN_PROCESS_ID)
                            .terms(
                                v ->
                                    v.value(
                                        processDefinitions.stream()
                                            .map(FieldValue::of)
                                            .collect(Collectors.toList())))));
      }
    }
    final Query applyTenantIdFilter =
        addFilterOnTenantIdIfRequired(query.build().toQuery(), tenantId);

    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(
        applyTenantIdFilter, isStartedByForm);
  }

  @Override
  public List<ProcessEntity> getProcessesStartedByForm() {
    final Query query =
        QueryBuilders.bool()
            .must(m -> m.exists(e -> e.field(ProcessIndex.BPMN_PROCESS_ID)))
            .mustNot(
                mn -> mn.term(t -> t.field(ProcessIndex.BPMN_PROCESS_ID).value(FieldValue.of(""))))
            .build()
            .toQuery();
    return getProcessEntityUniqueByProcessDefinitionIdAndTenantId(query, true);
  }

  private Query addFilterOnTenantIdIfRequired(final Query query, final String tenantId) {
    if (securityConfiguration.getMultiTenancy().isEnabled() && StringUtils.isNotBlank(tenantId)) {
      final Query tenantQuery =
          new Query.Builder()
              .term(term -> term.field(ProcessIndex.TENANT_ID).value(FieldValue.of(tenantId)))
              .build();
      return OpenSearchUtil.joinWithAnd(tenantQuery, query);
    }
    return query;
  }

  private List<ProcessEntity> getProcessEntityUniqueByProcessDefinitionIdAndTenantId(
      final Query query, final Boolean isStartedByForm) {

    final Builder processDefinitionAndTenantBucket =
        new Builder()
            .sources(
                List.of(
                    Map.of(
                        DEFINITION_ID_TERMS_SOURCE_NAME,
                        new CompositeAggregationSource.Builder()
                            .terms(t -> t.field(ProcessIndex.BPMN_PROCESS_ID))
                            .build()),
                    Map.of(
                        TENANT_ID_TERMS_SOURCE_NAME,
                        new CompositeAggregationSource.Builder()
                            .terms(t -> t.field(ProcessIndex.TENANT_ID))
                            .build())))
            .size(OpenSearchUtil.QUERY_MAX_SIZE);

    final TermsAggregation maxVersionDocTerm =
        new TermsAggregation.Builder()
            .field(ProcessIndex.VERSION)
            .order(Map.of("_key", SortOrder.Desc))
            .size(1)
            .build();

    final Aggregation topHitsAgg =
        AggregationBuilders.topHits()
            .sort(
                SortOptions.of(
                    s -> s.field(f -> f.field(ProcessIndex.VERSION).order(SortOrder.Desc))))
            .size(1)
            .build()
            ._toAggregation();

    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder().index(processIndex.getAlias()).query(query).size(0);

    if (isStartedByForm == null) {

      final var nestedAggregate =
          new Aggregation.Builder()
              .terms(maxVersionDocTerm)
              .aggregations(TOP_HITS_AGG_NAME, topHitsAgg)
              .build();

      searchRequest.aggregations(
          BPMN_PROCESS_ID_TENANT_ID_AGG_NAME,
          Aggregation.of(
              agg ->
                  agg.composite(processDefinitionAndTenantBucket.build())
                      .aggregations(MAX_VERSION_DOCUMENTS_AGG_NAME, nestedAggregate)));
    } else {
      final Query startedByFormFilter = startedByFormAggregateFilter(isStartedByForm);

      final var filterAggregate =
          new Aggregation.Builder()
              .filter(startedByFormFilter)
              .aggregations(TOP_HITS_AGG_NAME, topHitsAgg)
              .build();

      final var maxVersionAggregate =
          new Aggregation.Builder()
              .terms(maxVersionDocTerm)
              .aggregations(STARTED_BY_FORM_FILTERED_DOCS, filterAggregate)
              .build();

      searchRequest.aggregations(
          BPMN_PROCESS_ID_TENANT_ID_AGG_NAME,
          Aggregation.of(
              agg ->
                  agg.composite(processDefinitionAndTenantBucket.build())
                      .aggregations(MAX_VERSION_DOCUMENTS_AGG_NAME, maxVersionAggregate)));
    }

    try {
      final SearchResponse<ProcessEntity> response =
          tenantAwareClient.search(searchRequest, ProcessEntity.class);

      final CompositeAggregate composite =
          response.aggregations().get(BPMN_PROCESS_ID_TENANT_ID_AGG_NAME).composite();
      final List<Hit<JsonData>> hits =
          isStartedByForm != null
              ? getFilteredAggregateSearchHits(composite)
              : getAggregateSearchHits(composite);

      return OpenSearchUtil.mapSearchHits(hits, objectMapper, ProcessEntity.class);

    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private List<Hit<JsonData>> getFilteredAggregateSearchHits(final CompositeAggregate composite) {
    return composite.buckets().array().stream()
        .flatMap(
            bucket ->
                ((LongTermsAggregate)
                        bucket.aggregations().get(MAX_VERSION_DOCUMENTS_AGG_NAME)._get())
                    .buckets().array().stream()
                        .flatMap(
                            versionBucket -> {
                              final var startedByFormDocs =
                                  ((FilterAggregate)
                                          versionBucket
                                              .aggregations()
                                              .get(STARTED_BY_FORM_FILTERED_DOCS)
                                              ._get())
                                      .aggregations();
                              return ((TopHitsAggregate)
                                      startedByFormDocs.get(TOP_HITS_AGG_NAME)._get())
                                  .hits().hits().stream();
                            }))
        .collect(Collectors.toList());
  }

  private List<Hit<JsonData>> getAggregateSearchHits(final CompositeAggregate composite) {
    return composite.buckets().array().stream()
        .flatMap(
            bucket ->
                ((LongTermsAggregate)
                        bucket.aggregations().get(MAX_VERSION_DOCUMENTS_AGG_NAME)._get())
                    .buckets().array().stream()
                        .flatMap(
                            versionBucket ->
                                ((TopHitsAggregate)
                                        versionBucket.aggregations().get(TOP_HITS_AGG_NAME)._get())
                                    .hits().hits().stream()))
        .collect(Collectors.toList());
  }

  private Query startedByFormAggregateFilter(final boolean isStartedByForm) {
    final BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();
    if (isStartedByForm) {
      boolQueryBuilder
          .should(QueryBuilders.exists().field(ProcessIndex.FORM_KEY).build().toQuery())
          .should(QueryBuilders.exists().field(ProcessIndex.FORM_ID).build().toQuery())
          .minimumShouldMatch("1");
    } else {
      boolQueryBuilder
          .mustNot(QueryBuilders.exists().field(ProcessIndex.FORM_KEY).build().toQuery())
          .mustNot(QueryBuilders.exists().field(ProcessIndex.FORM_ID).build().toQuery())
          .minimumShouldMatch("1");
    }
    return boolQueryBuilder.build().toQuery();
  }
}
