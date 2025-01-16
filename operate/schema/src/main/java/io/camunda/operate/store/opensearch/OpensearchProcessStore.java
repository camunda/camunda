/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TERMS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations.TOPHITS_AGG_SIZE;
import static io.camunda.operate.store.opensearch.client.sync.OpenSearchRetryOperation.UPDATE_RETRY_COUNT;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.cardinalityAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.filtersAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.termAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.topHitsAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.withTenantCheck;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ALL;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.TREE_PATH;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.*;
import static java.util.function.UnaryOperator.identity;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.FiltersBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchProcessStore implements ProcessStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchProcessStore.class);
  private static final String DISTINCT_FIELD_COUNTS = "distinctFieldCounts";

  @Autowired private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  @Qualifier("operateProcessIndex")
  private ProcessIndex processIndex;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  @Override
  public Optional<Long> getDistinctCountFor(final String fieldName) {
    final SearchResponse<Void> response;
    final var searchRequestBuilder =
        searchRequestBuilder(processIndex.getAlias())
            .query(matchAll())
            .aggregations(
                DISTINCT_FIELD_COUNTS, cardinalityAggregation(fieldName, 1_000)._toAggregation())
            .size(0);

    try {
      response = richOpenSearchClient.doc().search(searchRequestBuilder, Void.class);
      return Optional.of(response.aggregations().get(DISTINCT_FIELD_COUNTS).cardinality().value());
    } catch (final Exception e) {
      LOGGER.error(
          String.format(
              "Error in distinct count for field %s in index alias %s.",
              fieldName, processIndex.getAlias()),
          e);
      return Optional.empty();
    }
  }

  @Override
  public void refreshIndices(final String... indices) {
    richOpenSearchClient.index().refresh(indices);
  }

  @Override
  public ProcessEntity getProcessByKey(final Long processDefinitionKey) {
    final var searchRequestBuilder =
        searchRequestBuilder(processIndex.getAlias())
            .query(withTenantCheck(term(ProcessIndex.KEY, processDefinitionKey)));

    return richOpenSearchClient
        .doc()
        .searchUnique(
            searchRequestBuilder, ProcessEntity.class, String.valueOf(processDefinitionKey));
  }

  @Override
  public String getDiagramByKey(final Long processDefinitionKey) {
    final var searchRequestBuilder =
        searchRequestBuilder(processIndex.getAlias())
            .query(withTenantCheck(ids(processDefinitionKey.toString())));

    return richOpenSearchClient
        .doc()
        .searchUnique(searchRequestBuilder, ProcessEntity.class, processDefinitionKey.toString())
        .getBpmnXml();
  }

  @Override
  public Map<ProcessKey, List<ProcessEntity>> getProcessesGrouped(
      final String tenantId, final Set<String> allowedBPMNProcessIds) {
    final String tenantsGroupsAggName = "group_by_tenantId";
    final String groupsAggName = "group_by_bpmnProcessId";
    final String processesAggName = "processes";
    final List<String> sourceFields =
        List.of(
            ProcessIndex.ID,
            ProcessIndex.NAME,
            ProcessIndex.VERSION,
            ProcessIndex.VERSION_TAG,
            ProcessIndex.BPMN_PROCESS_ID,
            ProcessIndex.TENANT_ID);
    final Query query =
        allowedBPMNProcessIds == null
            ? matchAll()
            : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowedBPMNProcessIds);
    final var searchRequestBuilder =
        searchRequestBuilder(processIndex.getAlias())
            .query(withTenantCheck(withTenantIdQuery(tenantId, query)))
            .size(0)
            .aggregations(
                tenantsGroupsAggName,
                withSubaggregations(
                    termAggregation(ProcessIndex.TENANT_ID, TERMS_AGG_SIZE),
                    Map.of(
                        groupsAggName,
                        withSubaggregations(
                            termAggregation(ProcessIndex.BPMN_PROCESS_ID, TERMS_AGG_SIZE),
                            Map.of(
                                processesAggName,
                                topHitsAggregation(
                                        sourceFields,
                                        TOPHITS_AGG_SIZE,
                                        sortOptions(ProcessIndex.VERSION, SortOrder.Desc))
                                    ._toAggregation())))));

    final SearchResponse<Object> response =
        richOpenSearchClient.doc().search(searchRequestBuilder, Object.class);
    final Map<ProcessKey, List<ProcessEntity>> result = new HashMap<>();

    response
        .aggregations()
        .get(tenantsGroupsAggName)
        .sterms()
        .buckets()
        .array()
        .forEach(
            tenantBucket ->
                tenantBucket
                    .aggregations()
                    .get(groupsAggName)
                    .sterms()
                    .buckets()
                    .array()
                    .forEach(
                        bpmnProcessIdBucket -> {
                          final String key = tenantBucket.key() + "_" + bpmnProcessIdBucket.key();
                          final List<ProcessEntity> value =
                              bpmnProcessIdBucket
                                  .aggregations()
                                  .get(processesAggName)
                                  .topHits()
                                  .hits()
                                  .hits()
                                  .stream()
                                  .map(h -> h.source().to(ProcessEntity.class))
                                  .toList();

                          result.put(new ProcessKey(key, tenantId), value);
                        }));

    return result;
  }

  @Override
  public Map<Long, ProcessEntity> getProcessesIdsToProcessesWithFields(
      final Set<String> allowedBPMNIds, final int maxSize, final String... fields) {
    final Query query =
        allowedBPMNIds == null
            ? matchAll()
            : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowedBPMNIds);
    final var searchRequestBuilder =
        searchRequestBuilder(processIndex.getAlias())
            .query(withTenantCheck(query))
            .source(sourceInclude(fields))
            .size(maxSize);

    return richOpenSearchClient
        .doc()
        .searchValues(searchRequestBuilder, ProcessEntity.class)
        .stream()
        .collect(Collectors.toMap(ProcessEntity::getKey, identity()));
  }

  @Override
  public long deleteProcessDefinitionsByKeys(final Long... processDefinitionKeys) {
    if (CollectionUtil.isEmpty(processDefinitionKeys)) {
      return 0;
    }
    return richOpenSearchClient
        .doc()
        .deleteByQuery(
            processIndex.getAlias(), longTerms(ProcessIndex.KEY, List.of(processDefinitionKeys)));
  }

  @Override
  public ProcessInstanceForListViewEntity getProcessInstanceListViewByKey(
      final Long processInstanceKey) {
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate, ALL)
            .query(
                withTenantCheck(
                    and(
                        ids(String.valueOf(processInstanceKey)),
                        term(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceKey))));

    return richOpenSearchClient
        .doc()
        .searchUnique(
            searchRequestBuilder,
            ProcessInstanceForListViewEntity.class,
            String.valueOf(processInstanceKey));
  }

  @Override
  public Map<String, Long> getCoreStatistics(final Set<String> allowedBPMNIds) {
    final Query incidentsQuery =
        and(term(INCIDENT, true), term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    final Query runningQuery = term(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE.name());
    final Query query =
        allowedBPMNIds == null
            ? matchAll()
            : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowedBPMNIds);
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate, ALL)
            .query(withTenantCheck(query))
            .aggregations(
                "agg",
                filtersAggregation(
                        Map.of(
                            "incidents", incidentsQuery,
                            "running", runningQuery))
                    ._toAggregation());

    final Map<String, FiltersBucket> buckets =
        richOpenSearchClient
            .doc()
            .search(searchRequestBuilder, Void.class)
            .aggregations()
            .get("agg")
            .filters()
            .buckets()
            .keyed();

    return Map.of(
        "running", buckets.get("running").docCount(),
        "incidents", buckets.get("incidents").docCount());
  }

  @Override
  public String getProcessInstanceTreePathById(final String processInstanceId) {
    record Result(String treePath) {}
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate)
            .query(
                withTenantCheck(
                    and(
                        term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                        term(KEY, processInstanceId))))
            .source(sourceInclude(TREE_PATH));

    return richOpenSearchClient
        .doc()
        .searchUnique(searchRequestBuilder, Result.class, processInstanceId)
        .treePath();
  }

  @Override
  public List<Map<String, String>> createCallHierarchyFor(
      final List<String> processInstanceIds, final String currentProcessInstanceId) {
    record Result(
        String id, String processDefinitionKey, String processName, String bpmnProcessId) {}
    final List<String> processInstanceIdsWithoutCurrentProcess =
        processInstanceIds.stream().filter(id -> !currentProcessInstanceId.equals(id)).toList();
    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate)
            .query(
                withTenantCheck(
                    and(
                        term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                        stringTerms(ID, processInstanceIdsWithoutCurrentProcess))))
            .source(sourceInclude(ID, PROCESS_KEY, PROCESS_NAME, BPMN_PROCESS_ID));

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, Result.class).stream()
        .map(
            r ->
                Map.of(
                    "instanceId", r.id(),
                    "processDefinitionId", r.processDefinitionKey(),
                    "processDefinitionName",
                        r.processName() != null ? r.processName() : r.bpmnProcessId()))
        .toList();
  }

  @Override
  public long deleteDocument(final String indexName, final String idField, final String id)
      throws IOException {
    return richOpenSearchClient.doc().delete(indexName, idField, id).deleted();
  }

  @Override
  public void deleteProcessInstanceFromTreePath(final String processInstanceKey) {
    record Result(String id, String treePath) {}
    record ProcessEntityUpdate(String treePath) {}

    // select process instance - get tree path
    final String treePath = getProcessInstanceTreePathById(processInstanceKey);

    // select all process instances with term treePath == tree path
    // update all this process instances to remove corresponding part of tree path
    // 2 cases:
    // - middle level: we remove /PI_key/FN_name/FNI_key from the middle
    // - end level: we remove /PI_key from the end

    final var searchRequestBuilder =
        searchRequestBuilder(listViewTemplate)
            .query(
                withTenantCheck(
                    and(
                        term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                        term(TREE_PATH, treePath),
                        not(term(KEY, processInstanceKey)))))
            .source(sourceInclude(ID, TREE_PATH));

    final List<Result> results = new ArrayList<>();
    final Map<String, String> idToIndex = new HashMap<>();
    final Consumer<List<Hit<Result>>> hitsConsumer =
        hits -> {
          for (final Hit<Result> hit : hits) {
            results.add(hit.source());
            idToIndex.put(hit.id(), hit.index());
          }
        };
    richOpenSearchClient.doc().scrollWith(searchRequestBuilder, Result.class, hitsConsumer);
    if (results.isEmpty()) {
      LOGGER.debug(
          "No results in deleteProcessInstanceFromTreePath for process instance key {}",
          processInstanceKey);
      return;
    }
    final var bulk = new BulkRequest.Builder();
    results.forEach(
        r ->
            bulk.operations(
                op ->
                    op.update(
                        upd -> {
                          final String index = idToIndex.get(r.id);
                          final String newTreePath =
                              new TreePath(r.treePath())
                                  .removeProcessInstance(processInstanceKey)
                                  .toString();

                          return upd.index(index)
                              .id(r.id)
                              .document(new ProcessEntityUpdate(newTreePath))
                              .retryOnConflict(UPDATE_RETRY_COUNT);
                        })));
    richOpenSearchClient.batch().bulk(bulk);
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstancesByProcessAndStates(
      final long processDefinitionKey,
      final Set<ProcessInstanceState> states,
      final int size,
      final String[] includeFields) {
    if (CollectionUtil.isEmpty(states)) {
      throw new OperateRuntimeException("Parameter 'states' is needed to search by states.");
    }

    final var searchRequest =
        searchRequestBuilder(listViewTemplate)
            .size(size)
            .query(
                withTenantCheck(
                    and(
                        term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                        term(PROCESS_KEY, processDefinitionKey),
                        stringTerms(
                            STATE, states.stream().map(Enum::name).collect(Collectors.toList())))))
            .source(sourceInclude(includeFields));
    return richOpenSearchClient
        .doc()
        .searchValues(searchRequest, ProcessInstanceForListViewEntity.class);
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstancesByParentKeys(
      final Set<Long> parentProcessInstanceKeys, final int size, final String[] includeFields) {
    if (CollectionUtil.isEmpty(parentProcessInstanceKeys)) {
      throw new OperateRuntimeException(
          "Parameter 'parentProcessInstanceKeys' is needed to search by parents.");
    }

    final var searchRequest =
        searchRequestBuilder(listViewTemplate)
            .query(
                withTenantCheck(
                    and(
                        term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                        longTerms(PARENT_PROCESS_INSTANCE_KEY, parentProcessInstanceKeys))))
            .source(sourceIncludesExcludes(includeFields, null));
    return richOpenSearchClient
        .doc()
        .scrollValues(searchRequest, ProcessInstanceForListViewEntity.class);
  }

  @Override
  public long deleteProcessInstancesAndDependants(final Set<Long> processInstanceKeys) {
    if (CollectionUtil.isEmpty(processInstanceKeys)) {
      return 0;
    }

    long count = 0;
    final List<ProcessInstanceDependant> processInstanceDependantsWithoutOperation =
        processInstanceDependantTemplates.stream()
            .filter(template -> !(template instanceof OperationTemplate))
            .toList();
    for (final ProcessInstanceDependant template : processInstanceDependantsWithoutOperation) {
      final String indexName = ((IndexTemplateDescriptor) template).getAlias();
      count +=
          richOpenSearchClient
              .doc()
              .deleteByQuery(
                  indexName,
                  longTerms(ProcessInstanceDependant.PROCESS_INSTANCE_KEY, processInstanceKeys));
    }
    count +=
        richOpenSearchClient
            .doc()
            .deleteByQuery(
                listViewTemplate.getAlias(),
                longTerms(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys));
    return count;
  }

  private Query withTenantIdQuery(@Nullable final String tenantId, @Nullable final Query query) {
    final Query tenantIdQ = tenantId != null ? term(ProcessIndex.TENANT_ID, tenantId) : null;

    if (query != null || tenantId != null) {
      return and(query, tenantIdQ);
    } else {
      return matchAll();
    }
  }
}
