/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.util.NamedValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.util.ElasticsearchTenantHelper;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
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
  private static final String TENANT_ID_TERMS_SOURCE_NAME = "group_by_tenant_id";
  private static final String MAX_VERSION_DOCUMENTS_AGG_NAME = "max_version_docs";
  private static final String STARTED_BY_FORM_FILTERED_DOCS = "started_by_form_docs";

  @Autowired private ProcessIndex processIndex;
  @Autowired private SecurityConfiguration securityConfiguration;

  @Qualifier("tasklistEsClient")
  @Autowired
  private ElasticsearchClient es8Client;

  @Autowired private ElasticsearchTenantHelper tenantHelper;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public ProcessEntity getProcessByProcessDefinitionKey(final String processDefinitionKey) {
    final var query = ElasticsearchUtil.termsQuery(ProcessIndex.KEY, processDefinitionKey);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var request =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(processIndex.getAlias())
            .query(tenantAwareQuery)
            .collapse(c -> c.field(ProcessIndex.KEY))
            .sort(ElasticsearchUtil.sortOrder(ProcessIndex.VERSION, SortOrder.Desc))
            .size(1)
            .build();

    try {
      final var response = es8Client.search(request, ProcessEntity.class);
      if (response.hits().total().value() > 0) {
        return response.hits().hits().get(0).source();
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
    final Query query;
    if (securityConfiguration.getMultiTenancy().isChecksEnabled()
        && StringUtils.isNotBlank(tenantId)) {
      final var bpmnProcessIdQuery =
          ElasticsearchUtil.termsQuery(ProcessIndex.BPMN_PROCESS_ID, bpmnProcessId);
      final var tenantIdQuery = ElasticsearchUtil.termsQuery(ProcessIndex.TENANT_ID, tenantId);
      query = ElasticsearchUtil.joinWithAnd(bpmnProcessIdQuery, tenantIdQuery);
    } else {
      query = ElasticsearchUtil.termsQuery(ProcessIndex.BPMN_PROCESS_ID, bpmnProcessId);
    }

    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var request =
        new SearchRequest.Builder()
            .index(processIndex.getAlias())
            .query(tenantAwareQuery)
            .collapse(c -> c.field(ProcessIndex.BPMN_PROCESS_ID))
            .sort(ElasticsearchUtil.sortOrder(ProcessIndex.VERSION, SortOrder.Desc))
            .size(1)
            .build();

    try {
      final var response = es8Client.search(request, ProcessEntity.class);
      if (response.hits().total().value() > 0) {
        return response.hits().hits().get(0).source();
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
    final var query = ElasticsearchUtil.termsQuery(ProcessIndex.KEY, processId);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var request =
        new SearchRequest.Builder().index(processIndex.getAlias()).query(tenantAwareQuery).build();

    try {
      final var response = es8Client.search(request, ProcessEntity.class);
      if (response.hits().total().value() == 1) {
        return response.hits().hits().get(0).source();
      } else if (response.hits().total().value() > 1) {
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
    // Early return if authorization is enabled but no processes are allowed
    if (securityConfiguration.getAuthorizations().isEnabled() && processDefinitions.isEmpty()) {
      return new ArrayList<>();
    }

    // Build base query: field exists AND field is not empty
    final Query existsQuery = ElasticsearchUtil.existsQuery(ProcessIndex.BPMN_PROCESS_ID);
    final Query notEmptyQuery =
        ElasticsearchUtil.mustNotQuery(
            ElasticsearchUtil.termsQuery(ProcessIndex.BPMN_PROCESS_ID, ""));

    // Add process definition filter if needed
    final Query query;
    if (securityConfiguration.getAuthorizations().isEnabled()
        && !processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
      final Query termsQuery =
          ElasticsearchUtil.termsQuery(ProcessIndex.BPMN_PROCESS_ID, processDefinitions);
      query = ElasticsearchUtil.joinWithAnd(termsQuery, existsQuery, notEmptyQuery);
    } else {
      query = ElasticsearchUtil.joinWithAnd(existsQuery, notEmptyQuery);
    }

    final Query finalQuery = enhanceQueryByTenantIdCheck(query, tenantId);
    return getProcessEntityUniqueByProcessDefinitionIdAndTenantIdEs8(finalQuery, isStartedByForm);
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
    final Query idQuery = ElasticsearchUtil.termsQuery(ProcessIndex.ID, search);
    final Query nameRegexpQuery =
        Query.of(
            q ->
                q.regexp(
                    r ->
                        r.field(ProcessIndex.NAME)
                            .value(regexSearch)
                            .caseInsensitive(CASE_INSENSITIVE)));
    final Query bpmnProcessIdRegexpQuery =
        Query.of(
            q ->
                q.regexp(
                    r ->
                        r.field(ProcessIndex.BPMN_PROCESS_ID)
                            .value(regexSearch)
                            .caseInsensitive(CASE_INSENSITIVE)));
    final Query existsQuery = ElasticsearchUtil.existsQuery(ProcessIndex.BPMN_PROCESS_ID);
    final Query notEmptyQuery = ElasticsearchUtil.termsQuery(ProcessIndex.BPMN_PROCESS_ID, "");

    Query query =
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.should(idQuery)
                            .should(nameRegexpQuery)
                            .should(bpmnProcessIdRegexpQuery)
                            .must(existsQuery)
                            .mustNot(notEmptyQuery)
                            .minimumShouldMatch("1")));

    if (securityConfiguration.getAuthorizations().isEnabled()) {
      if (processDefinitions.isEmpty()) {
        return new ArrayList<>();
      }
      if (!processDefinitions.contains(IdentityProperties.ALL_RESOURCES)) {
        final Query termsQuery =
            ElasticsearchUtil.termsQuery(ProcessIndex.BPMN_PROCESS_ID, processDefinitions);
        query = ElasticsearchUtil.joinWithAnd(query, termsQuery);
      }
    }
    final Query finalQuery = enhanceQueryByTenantIdCheck(query, tenantId);
    return getProcessEntityUniqueByProcessDefinitionIdAndTenantIdEs8(finalQuery, isStartedByForm);
  }

  @Override
  public List<ProcessEntity> getProcessesStartedByForm() {
    final Query existsQuery = ElasticsearchUtil.existsQuery(ProcessIndex.BPMN_PROCESS_ID);
    final Query notEmptyQuery =
        ElasticsearchUtil.mustNotQuery(
            ElasticsearchUtil.termsQuery(ProcessIndex.BPMN_PROCESS_ID, ""));
    final Query query = ElasticsearchUtil.joinWithAnd(existsQuery, notEmptyQuery);
    return getProcessEntityUniqueByProcessDefinitionIdAndTenantIdEs8(query, true);
  }

  private Query enhanceQueryByTenantIdCheck(final Query query, final String tenantId) {
    if (securityConfiguration.getMultiTenancy().isChecksEnabled()
        && StringUtils.isNotBlank(tenantId)) {
      final Query tenantIdQuery = ElasticsearchUtil.termsQuery(ProcessIndex.TENANT_ID, tenantId);
      return ElasticsearchUtil.joinWithAnd(tenantIdQuery, query);
    }

    return query;
  }

  // ES8 helper methods

  private Aggregation buildStartedByFormFilterAggEs8(
      final boolean isStartedByForm, final Aggregation topHitsAgg) {
    if (isStartedByForm) {
      return Aggregation.of(
          a ->
              a.filter(
                      f ->
                          f.bool(
                              b ->
                                  b.should(ElasticsearchUtil.existsQuery(ProcessIndex.FORM_KEY))
                                      .should(ElasticsearchUtil.existsQuery(ProcessIndex.FORM_ID))
                                      .minimumShouldMatch("1")))
                  .aggregations(TOP_HITS_AGG_NAME, topHitsAgg));
    } else {
      return Aggregation.of(
          a ->
              a.filter(
                      f ->
                          f.bool(
                              b ->
                                  b.mustNot(ElasticsearchUtil.existsQuery(ProcessIndex.FORM_KEY))
                                      .mustNot(ElasticsearchUtil.existsQuery(ProcessIndex.FORM_ID))
                                      .minimumShouldMatch("1")))
                  .aggregations(TOP_HITS_AGG_NAME, topHitsAgg));
    }
  }

  private List<ProcessEntity> getAggregateSearchHitsEs8(final Aggregate aggregate) {
    return aggregate.sterms().buckets().array().stream()
        .flatMap(
            bpmnProcessIdBucket -> {
              final Aggregate tenantIdBuckets =
                  bpmnProcessIdBucket.aggregations().get(TENANT_ID_TERMS_SOURCE_NAME);
              return tenantIdBuckets.sterms().buckets().array().stream()
                  .flatMap(
                      tenantIdBucket -> {
                        final Aggregate versionBuckets =
                            tenantIdBucket.aggregations().get(MAX_VERSION_DOCUMENTS_AGG_NAME);
                        return versionBuckets.lterms().buckets().array().stream()
                            .flatMap(
                                versionBucket -> {
                                  final Aggregate topHitsAgg =
                                      versionBucket.aggregations().get(TOP_HITS_AGG_NAME);
                                  return topHitsAgg.topHits().hits().hits().stream()
                                      .map(hit -> hit.source().to(ProcessEntity.class));
                                });
                      });
            })
        .toList();
  }

  private List<ProcessEntity> getFilteredAggregateSearchHitsEs8(final Aggregate aggregate) {
    return aggregate.sterms().buckets().array().stream()
        .flatMap(
            bpmnProcessIdBucket -> {
              final Aggregate tenantIdBuckets =
                  bpmnProcessIdBucket.aggregations().get(TENANT_ID_TERMS_SOURCE_NAME);
              return tenantIdBuckets.sterms().buckets().array().stream()
                  .flatMap(
                      tenantIdBucket -> {
                        final Aggregate versionBuckets =
                            tenantIdBucket.aggregations().get(MAX_VERSION_DOCUMENTS_AGG_NAME);
                        return versionBuckets.lterms().buckets().array().stream()
                            .flatMap(
                                versionBucket -> {
                                  final Aggregate filterAgg =
                                      versionBucket
                                          .aggregations()
                                          .get(STARTED_BY_FORM_FILTERED_DOCS);
                                  final Aggregate topHitsAgg =
                                      filterAgg.filter().aggregations().get(TOP_HITS_AGG_NAME);
                                  return topHitsAgg.topHits().hits().hits().stream()
                                      .map(hit -> hit.source().to(ProcessEntity.class));
                                });
                      });
            })
        .toList();
  }

  public List<ProcessEntity> getProcessEntityUniqueByProcessDefinitionIdAndTenantIdEs8(
      final Query query, final Boolean isStartedByForm) {

    // Build aggregations from innermost to outermost
    // Level 4: TopHits - get the actual document sorted by version DESC
    final Aggregation topHitsAgg =
        Aggregation.of(
            a ->
                a.topHits(
                    th ->
                        th.sort(ElasticsearchUtil.sortOrder(ProcessIndex.VERSION, SortOrder.Desc))
                            .size(1)));

    // Level 3: Terms on VERSION field - get max version (size=1, order by key DESC)
    final Aggregation maxVersionDocsAgg =
        Aggregation.of(
            a ->
                a.terms(
                    t ->
                        t.field(ProcessIndex.VERSION)
                            .size(1)
                            .order(NamedValue.of("_key", SortOrder.Desc))));

    // Level 2: Terms on TENANT_ID with optional filter for form-started processes
    final Aggregation tenantIdAgg;
    if (isStartedByForm != null) {
      // Add filter aggregation between version terms and topHits
      final Aggregation filterAgg = buildStartedByFormFilterAggEs8(isStartedByForm, topHitsAgg);
      final Aggregation maxVersionWithFilter =
          Aggregation.of(
              a ->
                  a.terms(
                          t ->
                              t.field(ProcessIndex.VERSION)
                                  .size(1)
                                  .order(NamedValue.of("_key", SortOrder.Desc)))
                      .aggregations(STARTED_BY_FORM_FILTERED_DOCS, filterAgg));
      tenantIdAgg =
          Aggregation.of(
              a ->
                  a.terms(
                          t ->
                              t.field(ProcessIndex.TENANT_ID)
                                  .size(ElasticsearchUtil.QUERY_MAX_SIZE))
                      .aggregations(MAX_VERSION_DOCUMENTS_AGG_NAME, maxVersionWithFilter));
    } else {
      final Aggregation maxVersionWithTopHits =
          Aggregation.of(
              a ->
                  a.terms(
                          t ->
                              t.field(ProcessIndex.VERSION)
                                  .size(1)
                                  .order(NamedValue.of("_key", SortOrder.Desc)))
                      .aggregations(TOP_HITS_AGG_NAME, topHitsAgg));
      tenantIdAgg =
          Aggregation.of(
              a ->
                  a.terms(
                          t ->
                              t.field(ProcessIndex.TENANT_ID)
                                  .size(ElasticsearchUtil.QUERY_MAX_SIZE))
                      .aggregations(MAX_VERSION_DOCUMENTS_AGG_NAME, maxVersionWithTopHits));
    }

    // Level 1: Terms on BPMN_PROCESS_ID
    final Aggregation bpmnProcessIdAgg =
        Aggregation.of(
            a ->
                a.terms(
                        t ->
                            t.field(ProcessIndex.BPMN_PROCESS_ID)
                                .size(ElasticsearchUtil.QUERY_MAX_SIZE))
                    .aggregations(TENANT_ID_TERMS_SOURCE_NAME, tenantIdAgg));

    final SearchRequest request =
        new SearchRequest.Builder()
            .index(processIndex.getAlias())
            .query(tenantHelper.makeQueryTenantAware(query))
            .aggregations(BPMN_PROCESS_ID_TENANT_ID_AGG_NAME, bpmnProcessIdAgg)
            .size(0)
            .build();

    try {
      final var response = es8Client.search(request, ProcessEntity.class);
      final Aggregate bpmnProcessIdBuckets =
          response.aggregations().get(BPMN_PROCESS_ID_TENANT_ID_AGG_NAME);

      if (isStartedByForm != null) {
        return getFilteredAggregateSearchHitsEs8(bpmnProcessIdBuckets);
      } else {
        return getAggregateSearchHitsEs8(bpmnProcessIdBuckets);
      }

    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }
}
