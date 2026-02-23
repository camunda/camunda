/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.operate.util.ElasticsearchUtil.MAP_CLASS;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.whereToSearch;
import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.BPMN_XML;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.TREE_PATH;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_NAME;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.STATE;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.store.ScrollException;
import io.camunda.operate.util.ElasticsearchTenantHelper;
import io.camunda.operate.util.ElasticsearchUtil;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchProcessStore implements ProcessStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchProcessStore.class);
  private static final String DISTINCT_FIELD_COUNTS = "distinctFieldCounts";
  private final ProcessIndex processIndex;

  private final ListViewTemplate listViewTemplate;

  private final List<ProcessInstanceDependant> processInstanceDependantTemplates;

  private final ElasticsearchClient esClient;

  private final OperateProperties operateProperties;

  private final ElasticsearchTenantHelper tenantHelper;

  public ElasticsearchProcessStore(
      final ProcessIndex processIndex,
      final ListViewTemplate listViewTemplate,
      final List<ProcessInstanceDependant> processInstanceDependantTemplates,
      final OperateProperties operateProperties,
      final ElasticsearchClient esClient,
      final ElasticsearchTenantHelper tenantHelper) {
    this.processIndex = processIndex;
    this.listViewTemplate = listViewTemplate;
    this.processInstanceDependantTemplates = processInstanceDependantTemplates;
    this.operateProperties = operateProperties;
    this.esClient = esClient;
    this.tenantHelper = tenantHelper;
  }

  @Override
  public Optional<Long> getDistinctCountFor(final String fieldName) {
    final String indexAlias = processIndex.getAlias();
    LOGGER.debug("Called distinct count for field {} in index alias {}.", fieldName, indexAlias);
    final var searchRequest =
        new SearchRequest.Builder()
            .index(indexAlias)
            .query(q -> q.matchAll(m -> m))
            .size(0)
            .aggregations(
                DISTINCT_FIELD_COUNTS,
                a -> a.cardinality(c -> c.precisionThreshold(1_000).field(fieldName)))
            .build();
    try {
      final var res = esClient.search(searchRequest, Void.class);
      final var distinctFieldCounts = res.aggregations().get(DISTINCT_FIELD_COUNTS).cardinality();

      return Optional.of(distinctFieldCounts.value());
    } catch (final Exception e) {
      LOGGER.error(
          String.format(
              "Error in distinct count for field %s in index alias %s.", fieldName, indexAlias),
          e);
      return Optional.empty();
    }
  }

  @Override
  public void refreshIndices(final String... indices) {
    if (indices == null || indices.length == 0) {
      throw new OperateRuntimeException("Refresh indices needs at least one index to refresh.");
    }
    try {
      esClient.indices().refresh(r -> r.index(Arrays.asList(indices)));
    } catch (final IOException ex) {
      throw new OperateRuntimeException("Failed to refresh indices " + Arrays.asList(indices), ex);
    }
  }

  @Override
  public ProcessEntity getProcessByKey(final Long processDefinitionKey) {
    final var query = ElasticsearchUtil.termsQuery(ProcessIndex.KEY, processDefinitionKey);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    try {
      final var res =
          esClient.search(
              s ->
                  s.index(processIndex.getAlias())
                      .query(tenantAwareQuery)
                      .source(src -> src.filter(f -> f.excludes(ProcessIndex.BPMN_XML))),
              ProcessEntity.class);
      if (res.hits().total().value() == 1) {
        return res.hits().hits().getFirst().source();
      } else if (res.hits().total().value() > 1) {
        throw new NotFoundException(
            String.format("Could not find unique process with key '%s'.", processDefinitionKey));
      } else {
        throw new NotFoundException(
            String.format("Could not find process with key '%s'.", processDefinitionKey));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public String getDiagramByKey(final Long processDefinitionKey) {
    final var idsQuery = ElasticsearchUtil.idsQuery(processDefinitionKey.toString());
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(idsQuery);

    try {
      final var res =
          esClient.search(
              s ->
                  s.index(processIndex.getAlias())
                      .query(tenantAwareQuery)
                      .source(src -> src.filter(f -> f.includes(BPMN_XML))),
              MAP_CLASS);

      if (res.hits().total().value() == 1) {
        return ElasticsearchUtil.getFieldFromResponseObject(res, BPMN_XML);
      } else if (res.hits().total().value() > 1) {
        throw new NotFoundException(
            String.format("Could not find unique process with id '%s'.", processDefinitionKey));
      } else {
        throw new NotFoundException(
            String.format("Could not find process with id '%s'.", processDefinitionKey));
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining the process diagram: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<ProcessKey, List<ProcessEntity>> getProcessesGrouped(
      final String tenantId, @Nullable final Set<String> allowedBPMNProcessIds) {

    final var query = buildQuery(tenantId, allowedBPMNProcessIds);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(processIndex.getAlias())
            .query(tenantAwareQuery)
            .source(
                src ->
                    src.filter(
                        f ->
                            f.includes(
                                Arrays.asList(
                                    ProcessIndex.ID,
                                    ProcessIndex.NAME,
                                    ProcessIndex.VERSION,
                                    ProcessIndex.VERSION_TAG,
                                    ProcessIndex.BPMN_PROCESS_ID,
                                    ProcessIndex.TENANT_ID))))
            .sort(so -> so.field(fs -> fs.field(ProcessIndex.VERSION).order(SortOrder.Desc)));

    try {
      final Map<ProcessKey, List<ProcessEntity>> result = new HashMap<>();

      ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, ProcessEntity.class)
          .flatMap(searchRes -> searchRes.hits().hits().stream())
          .map(Hit::source)
          .forEach(
              processEntity -> {
                final ProcessKey groupKey =
                    new ProcessKey(processEntity.getBpmnProcessId(), processEntity.getTenantId());
                result.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(processEntity);
              });

      return result;
    } catch (final Exception e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining grouped processes: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public Map<Long, ProcessEntity> getProcessesIdsToProcessesWithFields(
      @Nullable final Set<String> allowedBPMNIds, final int maxSize, final String... fields) {
    final Map<Long, ProcessEntity> map = new HashMap<>();

    final Query query;
    if (allowedBPMNIds == null) {
      query = Query.of(q -> q.matchAll(m -> m));
    } else {
      query = ElasticsearchUtil.termsQuery(BPMN_PROCESS_ID, allowedBPMNIds);
    }

    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    try {
      final var res =
          esClient.search(
              s ->
                  s.index(processIndex.getAlias())
                      .query(tenantAwareQuery)
                      .size(maxSize)
                      .source(src -> src.filter(f -> f.includes(Arrays.stream(fields).toList()))),
              ProcessEntity.class);

      res.hits().hits().stream()
          .forEach(
              h -> {
                final var entity = h.source();
                map.put(entity.getKey(), entity);
              });

      return map;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining processes: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public long deleteProcessDefinitionsByKeys(final Long... processDefinitionKeys) {
    if (processDefinitionKeys == null || processDefinitionKeys.length == 0) {
      return 0;
    }
    try {
      final var res =
          esClient.deleteByQuery(
              d ->
                  d.index(processIndex.getAlias())
                      .query(
                          ElasticsearchUtil.termsQuery(
                              ProcessIndex.KEY, Arrays.asList(processDefinitionKeys))));
      return res.deleted() == null ? 0 : res.deleted();
    } catch (final IOException ex) {
      throw new OperateRuntimeException("Failed to delete process definitions by keys", ex);
    }
  }

  @Override
  public ProcessInstanceForListViewEntity getProcessInstanceListViewByKey(
      final Long processInstanceKey) {
    try {
      final var query =
          ElasticsearchUtil.joinWithAnd(
              ElasticsearchUtil.idsQuery(String.valueOf(processInstanceKey)),
              ElasticsearchUtil.termsQuery(
                  ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceKey));

      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

      final var constantScoreQuery = ElasticsearchUtil.constantScoreQuery(tenantAwareQuery);

      final var res =
          esClient.search(
              s -> s.index(whereToSearch(listViewTemplate, ALL)).query(constantScoreQuery),
              ProcessInstanceForListViewEntity.class);
      if (res.hits().total().value() == 1 && res.hits().hits().size() == 1) {
        return res.hits().hits().getFirst().source();
      } else if (res.hits().total().value() > 1) {
        throw new NotFoundException(
            String.format(
                "Could not find unique process instance with id '%s'.", processInstanceKey));
      } else {
        throw new NotFoundException(
            (String.format("Could not find process instance with id '%s'.", processInstanceKey)));
      }
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  @Override
  public Map<String, Long> getCoreStatistics(@Nullable final Set<String> allowedBPMNIds) {
    final var incidentsAggQuery =
        ElasticsearchUtil.joinWithAnd(
            ElasticsearchUtil.termsQuery(INCIDENT, true),
            ElasticsearchUtil.termsQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    final var incidentsAgg = Aggregation.of(a -> a.filter(incidentsAggQuery));

    final var runningAggQuery = ElasticsearchUtil.termsQuery(STATE, ProcessInstanceState.ACTIVE);
    final var runningAgg = Aggregation.of(a -> a.filter(runningAggQuery));

    final Query query;
    if (allowedBPMNIds == null) {
      query = Query.of(q -> q.matchAll(m -> m));
    } else {
      query = ElasticsearchUtil.termsQuery(BPMN_PROCESS_ID, allowedBPMNIds);
    }
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    try {
      final var res =
          esClient.search(
              s ->
                  s.index(whereToSearch(listViewTemplate, ONLY_RUNTIME))
                      .query(tenantAwareQuery)
                      .aggregations("incidents", incidentsAgg)
                      .aggregations("running", runningAgg)
                      .size(0),
              Void.class);

      final var aggs = res.aggregations();
      final var runningCount = aggs.get("running").filter().docCount();
      final var incidentCount = aggs.get("incidents").filter().docCount();

      return Map.of("running", runningCount, "incidents", incidentCount);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining process instance core statistics: %s",
              e.getMessage());
      LOGGER.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public String getProcessInstanceTreePathById(final String processInstanceId) {
    final var query =
        ElasticsearchUtil.joinWithAnd(
            ElasticsearchUtil.termsQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
            ElasticsearchUtil.termsQuery(KEY, processInstanceId));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    try {
      final var res =
          esClient.search(
              s ->
                  s.index(whereToSearch(listViewTemplate, ALL))
                      .query(tenantAwareQuery)
                      .source(src -> src.filter(f -> f.includes(TREE_PATH))),
              MAP_CLASS);
      if (res.hits().total().value() > 0) {
        return String.valueOf(res.hits().hits().getFirst().source().get(TREE_PATH));
      } else {
        throw new NotFoundException(
            String.format("Process instance not found: %s", processInstanceId));
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining tree path for process instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<Map<String, String>> createCallHierarchyFor(
      final List<String> processInstanceIds, final String currentProcessInstanceId) {
    final List<Map<String, String>> callHierarchy = new ArrayList<>();

    final List<String> processInstanceIdsWithoutCurrentProcess =
        new ArrayList<>(processInstanceIds);
    // remove id of current process instance
    processInstanceIdsWithoutCurrentProcess.remove(currentProcessInstanceId);

    final var q =
        ElasticsearchUtil.joinWithAnd(
            ElasticsearchUtil.termsQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
            ElasticsearchUtil.termsQuery(ID, processInstanceIdsWithoutCurrentProcess));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(q);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(whereToSearch(listViewTemplate, ALL))
            .source(
                src -> src.filter(f -> f.includes(ID, PROCESS_KEY, PROCESS_NAME, BPMN_PROCESS_ID)))
            .query(tenantAwareQuery);

    try {
      final var resStream =
          ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, MAP_CLASS);

      resStream
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .forEach(
              resMap ->
                  callHierarchy.add(
                      Map.of(
                          "instanceId",
                          String.valueOf(resMap.get(ID)),
                          "processDefinitionId",
                          String.valueOf(resMap.get(PROCESS_KEY)),
                          "processDefinitionName",
                          String.valueOf(
                              resMap.getOrDefault(PROCESS_NAME, resMap.get(BPMN_PROCESS_ID))))));
    } catch (final ScrollException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining process instance call hierarchy: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return callHierarchy;
  }

  @Override
  public long deleteDocument(final String indexName, final String idField, final String id)
      throws IOException {
    final var res =
        esClient.deleteByQuery(
            q -> q.index(indexName).query(ElasticsearchUtil.termsQuery(idField, id)));
    LOGGER.debug("Delete document {} in {} failures: {}", id, indexName, res.failures());
    return res.deleted() == null ? 0 : res.deleted();
  }

  @Override
  public void deleteProcessInstanceFromTreePath(final String processInstanceKey) {
    final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
    // select process instance - get tree path
    final String treePath = getProcessInstanceTreePathById(processInstanceKey);

    // select all process instances with term treePath == tree path
    // update all this process instances to remove corresponding part of tree path
    // 2 cases:
    // - middle level: we remove /PI_key/FN_name/FNI_key from the middle
    // - end level: we remove /PI_key from the end

    final var query =
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.must(
                                ElasticsearchUtil.termsQuery(
                                    JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                                ElasticsearchUtil.termsQuery(TREE_PATH, treePath))
                            .mustNot(ElasticsearchUtil.termsQuery(KEY, processInstanceKey))));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(whereToSearch(listViewTemplate, ALL))
            .query(tenantAwareQuery)
            .source(s -> s.filter(f -> f.includes(TREE_PATH)));
    try {
      final var resStream =
          ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, MAP_CLASS);
      resStream
          .flatMap(res -> res.hits().hits().stream())
          .forEach(
              hit -> {
                final Map<String, Object> updateFields = new HashMap<>();
                final String newTreePath =
                    new TreePath((String) hit.source().get(TREE_PATH))
                        .removeProcessInstance(processInstanceKey)
                        .toString();
                updateFields.put(TREE_PATH, newTreePath);
                bulkRequest.operations(
                    op ->
                        op.update(
                            u ->
                                u.index(hit.index())
                                    .id(hit.id())
                                    .retryOnConflict(UPDATE_RETRY_COUNT)
                                    .action(a -> a.doc(updateFields))));
              });

      ElasticsearchUtil.processBulkRequest(
          esClient,
          bulkRequest,
          operateProperties.getElasticsearch().getBulkRequestMaxSizeInBytes());
    } catch (final Exception e) {
      throw new OperateRuntimeException(
          String.format(
              "Exception occurred when deleting process instance %s from tree path: %s",
              processInstanceKey, e.getMessage()));
    }
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstancesByProcessAndStates(
      final long processDefinitionKey,
      final Set<ProcessInstanceState> states,
      final int size,
      final String[] includeFields) {

    if (states == null || states.isEmpty()) {
      throw new OperateRuntimeException("Parameter 'states' is needed to search by states.");
    }

    final var query =
        joinWithAnd(
            ElasticsearchUtil.termsQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
            ElasticsearchUtil.termsQuery(PROCESS_KEY, processDefinitionKey),
            ElasticsearchUtil.termsQuery(STATE, states.stream().map(Enum::name).toList()));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    try {
      final List<String> nullSideIncludeFields =
          includeFields == null ? Collections.emptyList() : Arrays.asList(includeFields);
      final var source = SourceConfig.of(sc -> sc.filter(f -> f.includes(nullSideIncludeFields)));
      final var res =
          esClient.search(
              s ->
                  s.index(whereToSearch(listViewTemplate, ALL))
                      .query(tenantAwareQuery)
                      .size(size)
                      .source(source),
              ProcessInstanceForListViewEntity.class);
      return res.hits().hits().stream().map(Hit::source).toList();
    } catch (final IOException ex) {
      throw new OperateRuntimeException(
          String.format(
              "Failed to search process instances by processDefinitionKey [%s] and states [%s]",
              processDefinitionKey, states),
          ex);
    }
  }

  @Override
  public List<ProcessInstanceForListViewEntity> getProcessInstancesByParentKeys(
      final Set<Long> parentProcessInstanceKeys, final int size, final String[] includeFields) {

    if (parentProcessInstanceKeys == null || parentProcessInstanceKeys.isEmpty()) {
      throw new OperateRuntimeException(
          "Parameter 'parentProcessInstanceKeys' is needed to search by parents.");
    }

    final var q =
        ElasticsearchUtil.joinWithAnd(
            ElasticsearchUtil.termsQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
            ElasticsearchUtil.termsQuery(PARENT_PROCESS_INSTANCE_KEY, parentProcessInstanceKeys));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(q);

    final List<String> nulLSafeIncludeField =
        includeFields == null ? Collections.emptyList() : Arrays.asList(includeFields);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(whereToSearch(listViewTemplate, ALL))
            .size(size)
            .query(tenantAwareQuery)
            .source(s -> s.filter(f -> f.includes(nulLSafeIncludeField)));

    try {
      return ElasticsearchUtil.scrollAllStream(
              esClient, searchRequestBuilder, ProcessInstanceForListViewEntity.class)
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .toList();

    } catch (final ScrollException ex) {
      throw new OperateRuntimeException(
          "Failed to search process instances by parentProcessInstanceKeys", ex);
    }
  }

  @Override
  public long deleteProcessInstancesAndDependants(final Set<Long> processInstanceKeys) {
    if (processInstanceKeys == null || processInstanceKeys.isEmpty()) {
      return 0;
    }

    long count = 0;
    final List<ProcessInstanceDependant> processInstanceDependantsWithoutOperation =
        processInstanceDependantTemplates.stream()
            .filter(template -> !(template instanceof OperationTemplate))
            .toList();
    try {
      for (final ProcessInstanceDependant template : processInstanceDependantsWithoutOperation) {
        final String indexName = ((IndexTemplateDescriptor) template).getAlias();
        final var res =
            esClient.deleteByQuery(
                q ->
                    q.index(indexName)
                        .query(
                            ElasticsearchUtil.termsQuery(
                                ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
                                processInstanceKeys)));
        count += res.deleted() == null ? 0 : res.deleted();
      }

      final var res =
          esClient.deleteByQuery(
              q ->
                  q.index(listViewTemplate.getAlias())
                      .query(
                          ElasticsearchUtil.termsQuery(
                              ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys)));
      count += res.deleted() == null ? 0 : res.deleted();
    } catch (final IOException ex) {
      throw new OperateRuntimeException(
          "Failed to delete process instances and dependants by keys", ex);
    }

    return count;
  }

  @Override
  public long count() throws IOException {
    final var countRequest =
        new CountRequest.Builder()
            .index(processIndex.getAlias())
            .query(ElasticsearchUtil.matchAllQuery())
            .build();
    return esClient.count(countRequest).count();
  }

  private Query buildQuery(final String tenantId, final Set<String> allowedBPMNProcessIds) {
    final var bpmnQuery =
        allowedBPMNProcessIds != null
            ? ElasticsearchUtil.termsQuery(BPMN_PROCESS_ID, allowedBPMNProcessIds)
            : null;
    final var tenantIdQuery =
        tenantId != null ? ElasticsearchUtil.termsQuery(ProcessIndex.TENANT_ID, tenantId) : null;

    final var query = ElasticsearchUtil.joinWithAnd(bpmnQuery, tenantIdQuery);

    if (query == null) {
      return Query.of(q -> q.matchAll(m -> m));
    }

    return query;
  }
}
