/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.TreePath;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.FiltersBucket;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TREE_PATH;
import static io.camunda.operate.schema.templates.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.INCIDENT;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_KEY;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_NAME;
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
import static java.util.function.UnaryOperator.identity;

@Conditional(OpensearchCondition.class)
@Component
public class OpenSearchProcessStore implements ProcessStore {
  private static final Logger logger = LoggerFactory.getLogger(OpenSearchProcessStore.class);
  private static final String DISTINCT_FIELD_COUNTS = "distinctFieldCounts";

  @Autowired
  private RichOpenSearchClient richOpenSearchClient;

  @Autowired
  private ProcessIndex processIndex;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Override
  public Optional<Long> getDistinctCountFor(String fieldName) {
    final SearchResponse<Void> response;
    var searchRequestBuilder = searchRequestBuilder(processIndex.getAlias())
      .query(withTenantCheck(matchAll()))
      .aggregations(DISTINCT_FIELD_COUNTS, cardinalityAggregation(fieldName, 1_000)._toAggregation())
      .size(0);

    try {
      response = richOpenSearchClient.doc().search(searchRequestBuilder, Void.class);
      return Optional.of(response.aggregations().get(DISTINCT_FIELD_COUNTS).cardinality().value());
    } catch (Exception e) {
      logger.error(String.format("Error in distinct count for field %s in index alias %s.", fieldName, processIndex.getAlias()), e);
      return Optional.empty();
    }
  }

  @Override
  public ProcessEntity getProcessByKey(Long processDefinitionKey) {
    var searchRequestBuilder = searchRequestBuilder(processIndex.getAlias())
      .query(withTenantCheck(term(ProcessIndex.KEY, processDefinitionKey)));

    return richOpenSearchClient.doc().searchUnique(searchRequestBuilder, ProcessEntity.class, String.valueOf(processDefinitionKey));
  }

  @Override
  public String getDiagramByKey(Long processDefinitionKey) {
    var searchRequestBuilder = searchRequestBuilder(processIndex.getAlias())
      .query(withTenantCheck(ids(processDefinitionKey.toString())));

    return richOpenSearchClient.doc().searchUnique(searchRequestBuilder, ProcessEntity.class, processDefinitionKey.toString()).getBpmnXml();
  }

  private Query withTenantIdQuery(@Nullable String tenantId, @Nullable Query query) {
    final Query tenantIdQ = tenantId != null ? term(ProcessIndex.TENANT_ID, tenantId) : null;

    if(query != null || tenantId != null) {
      return and(query, tenantIdQ);
    } else {
      return matchAll();
    }
  }

  @Override
  public Map<ProcessKey, List<ProcessEntity>> getProcessesGrouped(String tenantId, Set<String> allowedBPMNProcessIds) {
    final String tenantsGroupsAggName = "group_by_tenantId";
    final String groupsAggName = "group_by_bpmnProcessId";
    final String processesAggName = "processes";
    final List<String> sourceFields = List.of(ProcessIndex.ID, ProcessIndex.NAME, ProcessIndex.VERSION, ProcessIndex.BPMN_PROCESS_ID, ProcessIndex.TENANT_ID);
    final Query query = allowedBPMNProcessIds == null ? matchAll() : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowedBPMNProcessIds);
    var searchRequestBuilder = searchRequestBuilder(processIndex.getAlias())
      .query(withTenantCheck(withTenantIdQuery(tenantId, query)))
      .size(0)
      .aggregations(tenantsGroupsAggName, withSubaggregations(
        termAggregation(ProcessIndex.TENANT_ID, TERMS_AGG_SIZE),
        Map.of(groupsAggName, withSubaggregations(
            termAggregation(ProcessIndex.BPMN_PROCESS_ID, TERMS_AGG_SIZE),
            Map.of(processesAggName, topHitsAggregation(sourceFields, TOPHITS_AGG_SIZE, sortOptions(ProcessIndex.VERSION, SortOrder.Desc))._toAggregation())
        ))
      ));

    final SearchResponse<Object> response = richOpenSearchClient.doc().search(searchRequestBuilder, Object.class);
    final Map<ProcessKey, List<ProcessEntity>> result = new HashMap<>();

    response.aggregations().get(tenantsGroupsAggName).sterms().buckets().array().forEach(tenantBucket ->
      tenantBucket.aggregations().get(groupsAggName).sterms().buckets().array().forEach(bpmnProcessIdBucket -> {
        final String key = tenantBucket.key() + "_" + bpmnProcessIdBucket.key();
        final List<ProcessEntity> value = bpmnProcessIdBucket.aggregations().get(processesAggName).topHits().hits().hits().stream()
          .map(h -> h.source().to(ProcessEntity.class))
          .toList();

        result.put(new ProcessKey(key, tenantId), value);
      })
    );

    return result;
  }

  @Override
  public Map<Long, ProcessEntity> getProcessesIdsToProcessesWithFields(Set<String> allowedBPMNIds,
                                                                       int maxSize, String... fields) {
    final Query query = allowedBPMNIds == null ? matchAll() : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowedBPMNIds);
    var searchRequestBuilder = searchRequestBuilder(processIndex.getAlias())
      .query(withTenantCheck(query))
      .source(sourceInclude(fields))
      .size(maxSize);

    return richOpenSearchClient.doc().searchValues(searchRequestBuilder, ProcessEntity.class)
      .stream()
      .collect(Collectors.toMap(
        ProcessEntity::getKey,
        identity()
      ));
  }

  @Override
  public ProcessInstanceForListViewEntity getProcessInstanceListViewByKey(Long processInstanceKey) {
    var searchRequestBuilder = searchRequestBuilder(listViewTemplate, ALL)
      .query(
          withTenantCheck(
            and(
              ids(String.valueOf(processInstanceKey)),
              term(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceKey)
            )
      ));

    return richOpenSearchClient.doc().searchUnique(searchRequestBuilder, ProcessInstanceForListViewEntity.class, String.valueOf(processInstanceKey));
  }

  @Override
  public Map<String, Long> getCoreStatistics(Set<String> allowedBPMNIds) {
    final Query incidentsQuery = and(
      term(INCIDENT, true),
      term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION)
    );
    final Query runningQuery = term(ListViewTemplate.STATE, ProcessInstanceState.ACTIVE.name());
    final Query query = allowedBPMNIds == null ? matchAll() : stringTerms(ListViewTemplate.BPMN_PROCESS_ID, allowedBPMNIds);
    var searchRequestBuilder = searchRequestBuilder(listViewTemplate, ALL)
      .query(withTenantCheck(query))
      .aggregations("agg", filtersAggregation(Map.of(
        "incidents", incidentsQuery,
        "running", runningQuery
        ))._toAggregation()
      );

    final Map<String, FiltersBucket> buckets = richOpenSearchClient.doc().search(searchRequestBuilder, Void.class)
      .aggregations()
      .get("agg")
      .filters()
      .buckets()
      .keyed();

    return Map.of(
      "running", buckets.get("running").docCount(),
      "incidents", buckets.get("incidents").docCount()
    );
  }

  @Override
  public String getProcessInstanceTreePathById(String processInstanceId) {
    record Result(String treePath){}
    var searchRequestBuilder = searchRequestBuilder(listViewTemplate)
      .query(withTenantCheck(
        and(
          term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
          term(KEY, processInstanceId)
        )
      ))
      .source(sourceInclude(TREE_PATH));

    return richOpenSearchClient.doc().searchUnique(searchRequestBuilder, Result.class, processInstanceId)
      .treePath();
  }

  @Override
  public List<Map<String, String>> createCallHierarchyFor(List<String> processInstanceIds, String currentProcessInstanceId) {
    record Result(String id, String processDefinitionKey, String processName, String bpmnProcessId){}
    final List<String> processInstanceIdsWithoutCurrentProcess = processInstanceIds.stream().filter(id -> ! currentProcessInstanceId.equals(id)).toList();
    var searchRequestBuilder = searchRequestBuilder(listViewTemplate)
      .query(withTenantCheck(
        and(
          term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
          stringTerms(ID, processInstanceIdsWithoutCurrentProcess)
        )
      ))
      .source(sourceInclude(ID, PROCESS_KEY, PROCESS_NAME, BPMN_PROCESS_ID));

    return richOpenSearchClient.doc().scrollValues(searchRequestBuilder, Result.class)
      .stream().map(r ->
        Map.of(
          "instanceId", r.id(),
          "processDefinitionId", r.processDefinitionKey(),
          "processDefinitionName", r.processName() != null ? r.processName() : r.bpmnProcessId()
        )
      ).toList();
  }

  @Override
  public long deleteDocument(String indexName, String idField, String id) throws IOException {
    return richOpenSearchClient.doc().delete(indexName, idField, id).deleted();
  }

  @Override
  public void deleteProcessInstanceFromTreePath(String processInstanceKey) {
    record Result(String id, String treePath){}
    record ProcessEntityUpdate(String treePath){}

    // select process instance - get tree path
    String treePath = getProcessInstanceTreePathById(processInstanceKey);

    // select all process instances with term treePath == tree path
    // update all this process instances to remove corresponding part of tree path
    // 2 cases:
    // - middle level: we remove /PI_key/FN_name/FNI_key from the middle
    // - end level: we remove /PI_key from the end

    var searchRequestBuilder = searchRequestBuilder(listViewTemplate)
      .query(withTenantCheck(
        and(
          term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
          term(TREE_PATH, treePath),
          not(term(KEY, processInstanceKey))
        )
      ))
      .source(sourceInclude(TREE_PATH));

    var results = richOpenSearchClient.doc().scrollValues(searchRequestBuilder, Result.class);
    if(results.isEmpty()){
      logger.debug("No results in deleteProcessInstanceFromTreePath for process instance key {}", processInstanceKey);
      return;
    }
    var bulk = new BulkRequest.Builder();
    results .forEach(r ->
      bulk.operations(op ->
        op.update(upd -> {
          String newTreePath = new TreePath(r.treePath()).removeProcessInstance(processInstanceKey).toString();

          return upd.index(listViewTemplate.getFullQualifiedName())
            .id(r.id)
            .document(new ProcessEntityUpdate(newTreePath))
            .retryOnConflict(UPDATE_RETRY_COUNT);
        })
      )
    );
    richOpenSearchClient.batch().bulk(bulk);
  }
}
