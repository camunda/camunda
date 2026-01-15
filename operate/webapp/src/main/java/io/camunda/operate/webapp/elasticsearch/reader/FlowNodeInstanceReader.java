/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.store.elasticsearch.ElasticsearchIncidentStore.ACTIVE_INCIDENT_QUERY_ES8;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.TERMS_AGG_SIZE;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.searchAfterToFieldValues;
import static io.camunda.operate.util.ElasticsearchUtil.termsQuery;
import static io.camunda.operate.util.ElasticsearchUtil.whereToSearch;
import static io.camunda.operate.webapp.rest.dto.incidents.IncidentDto.FALLBACK_PROCESS_DEFINITION_NAME;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.DECISION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_NAME;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ELEMENT_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATION_DATE;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.END_DATE;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.LEVEL;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.START_DATE;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.TREE_PATH;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.TYPE;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.ACTIVE;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.COMPLETED;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.TERMINATED;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.Buckets;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.util.NamedValue;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.ScrollException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ElasticsearchUtil.QueryType;
import io.camunda.operate.webapp.data.IncidentDataHolder;
import io.camunda.operate.webapp.rest.FlowNodeInstanceMetadataBuilder;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeStateDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.metadata.DecisionInstanceReferenceDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceBreadcrumbEntryDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadata;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class FlowNodeInstanceReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.FlowNodeInstanceReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlowNodeInstanceReader.class);

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private ProcessCache processCache;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private IncidentReader incidentReader;

  @Autowired private FlowNodeInstanceMetadataBuilder flowNodeInstanceMetadataBuilder;

  @Override
  public Map<String, FlowNodeInstanceResponseDto> getFlowNodeInstances(
      final FlowNodeInstanceRequestDto request) {
    final Map<String, FlowNodeInstanceResponseDto> response = new HashMap<>();
    for (final FlowNodeInstanceQueryDto query : request.getQueries()) {
      response.put(query.getTreePath(), getFlowNodeInstances(query));
    }
    return response;
  }

  @Override
  public FlowNodeMetadataDto getFlowNodeMetadata(
      final String processInstanceId, final FlowNodeMetadataRequestDto request) {
    if (request.getFlowNodeId() != null) {
      return getMetadataByFlowNodeId(
          processInstanceId, request.getFlowNodeId(), request.getFlowNodeType());
    } else if (request.getFlowNodeInstanceId() != null) {
      return getMetadataByFlowNodeInstanceId(request.getFlowNodeInstanceId());
    }
    return null;
  }

  @Deprecated
  @Override
  public Map<String, FlowNodeStateDto> getFlowNodeStates(final String processInstanceId) {
    final var query =
        ElasticsearchUtil.constantScoreQuery(termsQuery(PROCESS_INSTANCE_KEY, processInstanceId));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    // Build not completed flow nodes aggregation (ACTIVE or TERMINATED)
    final var notCompletedFlowNodesAggs =
        new Aggregation.Builder()
            .filter(
                f ->
                    f.bool(
                        b -> b.must(termsQuery(STATE, List.of(ACTIVE.name(), TERMINATED.name())))))
            .aggregations(
                ACTIVE_FLOW_NODES_BUCKETS_AGG_NAME,
                new Aggregation.Builder()
                    .terms(t -> t.field(FLOW_NODE_ID).size(TERMS_AGG_SIZE))
                    .aggregations(
                        LATEST_FLOW_NODE_AGG_NAME,
                        new Aggregation.Builder()
                            .topHits(
                                th ->
                                    th.sort(ElasticsearchUtil.sortOrder(START_DATE, SortOrder.Desc))
                                        .size(1)
                                        .source(s -> s.filter(sf -> sf.includes(STATE, TREE_PATH))))
                            .build())
                    .build())
            .build();

    // Build finished flow nodes aggregation
    final var finishedFlowNodesAggs =
        new Aggregation.Builder()
            .filter(f -> f.bool(b -> b.must(termsQuery(STATE, COMPLETED.name()))))
            .aggregations(
                FINISHED_FLOW_NODES_BUCKETS_AGG_NAME,
                new Aggregation.Builder()
                    .terms(t -> t.field(FLOW_NODE_ID).size(TERMS_AGG_SIZE))
                    .build())
            .build();

    // Build incidents aggregation
    final var incidentsAggs =
        new Aggregation.Builder()
            .filter(f -> f.bool(b -> b.must(termsQuery(INCIDENT, true))))
            .aggregations(
                AGG_INCIDENT_PATHS,
                new Aggregation.Builder()
                    .terms(t -> t.field(TREE_PATH).size(TERMS_AGG_SIZE))
                    .build())
            .build();

    final var request =
        new SearchRequest.Builder()
            .index(whereToSearch(flowNodeInstanceTemplate, ALL))
            .query(tenantAwareQuery)
            .aggregations(ACTIVE_FLOW_NODES_AGG_NAME, notCompletedFlowNodesAggs)
            .aggregations(AGG_INCIDENTS, incidentsAggs)
            .aggregations(FINISHED_FLOW_NODES_AGG_NAME, finishedFlowNodesAggs)
            .size(0)
            .build();

    try {
      final var response = esClient.search(request, FlowNodeInstanceEntity.class);

      final Set<String> incidentPaths = new HashSet<>();
      processAggregation(response.aggregations(), incidentPaths, new AtomicBoolean());

      final Set<String> finishedFlowNodes =
          collectFinishedFlowNodes(response.aggregations().get(FINISHED_FLOW_NODES_AGG_NAME));

      final Map<String, FlowNodeStateDto> result = new HashMap<>();
      collectActiveFlowNodeStates(
          response.aggregations().get(ACTIVE_FLOW_NODES_AGG_NAME), incidentPaths, result);

      // add finished when needed
      for (final var finishedFlowNodeId : finishedFlowNodes) {
        if (result.get(finishedFlowNodeId) == null) {
          result.put(finishedFlowNodeId, FlowNodeStateDto.COMPLETED);
        }
      }
      return result;
    } catch (final IOException e) {
      final var message =
          String.format(
              "Exception occurred, while obtaining states for instance flow nodes: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<Long> getFlowNodeInstanceKeysByIdAndStates(
      final Long processInstanceId, final String flowNodeId, final List<FlowNodeState> states) {
    try {
      final var stateNames = states.stream().map(Enum::name).toList();
      final var query =
          new BoolQuery.Builder()
              .must(termsQuery(FLOW_NODE_ID, flowNodeId))
              .must(termsQuery(PROCESS_INSTANCE_KEY, processInstanceId))
              .must(termsQuery(STATE, stateNames))
              .build()
              ._toQuery();
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

      final var searchRequest =
          new SearchRequest.Builder()
              .index(flowNodeInstanceTemplate.getAlias())
              .query(tenantAwareQuery)
              .fields(f -> f.field(ID))
              .source(s -> s.fetch(false))
              .build();

      final var response = esClient.search(searchRequest, FlowNodeInstanceEntity.class);

      return response.hits().hits().stream()
          .map(hit -> Long.parseLong(hit.fields().get(ID).to(List.class).get(0).toString()))
          .toList();
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          String.format("Could not retrieve flowNodeInstanceKey for flowNodeId %s ", flowNodeId),
          e);
    }
  }

  @Override
  public List<FlowNodeInstanceEntity> getAllFlowNodeInstances(final Long processInstanceKey) {
    final var query =
        ElasticsearchUtil.constantScoreQuery(
            ElasticsearchUtil.termsQuery(
                FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey));

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(ElasticsearchUtil.whereToSearch(flowNodeInstanceTemplate, QueryType.ALL))
            .query(query)
            .sort(ElasticsearchUtil.sortOrder(FlowNodeInstanceTemplate.POSITION, SortOrder.Asc));

    try {
      return ElasticsearchUtil.scrollAllStream(
              esClient, searchRequestBuilder, FlowNodeInstanceEntity.class)
          .flatMap(res -> res.hits().hits().stream())
          .map(Hit::source)
          .toList();
    } catch (final ScrollException e) {
      throw new OperateRuntimeException(e);
    }
  }

  private FlowNodeInstanceResponseDto getFlowNodeInstances(final FlowNodeInstanceQueryDto request) {
    final FlowNodeInstanceResponseDto response = queryFlowNodeInstances(request);
    // query one additional instance
    if (request.getSearchAfterOrEqual() != null || request.getSearchBeforeOrEqual() != null) {
      adjustResponse(response, request);
    }
    return response;
  }

  /**
   * In case of searchAfterOrEqual and searchBeforeOrEqual add additional entity either at the
   * beginning of the list, or at the end, to conform with "orEqual" part.
   *
   * @param response
   * @param request
   */
  private void adjustResponse(
      final FlowNodeInstanceResponseDto response, final FlowNodeInstanceQueryDto request) {
    String flowNodeInstanceId = null;
    if (request.getSearchAfterOrEqual() != null) {
      flowNodeInstanceId = (String) request.getSearchAfterOrEqual(objectMapper)[1];
    } else if (request.getSearchBeforeOrEqual() != null) {
      flowNodeInstanceId = (String) request.getSearchBeforeOrEqual(objectMapper)[1];
    }

    final FlowNodeInstanceQueryDto newRequest =
        request
            .createCopy()
            .setSearchAfter(null)
            .setSearchAfterOrEqual(null)
            .setSearchBefore(null)
            .setSearchBeforeOrEqual(null);

    final List<FlowNodeInstanceDto> entities =
        queryFlowNodeInstances(newRequest, flowNodeInstanceId).getChildren();
    if (entities.size() > 0) {
      final FlowNodeInstanceDto entity = entities.get(0);
      final List<FlowNodeInstanceDto> children = response.getChildren();
      if (request.getSearchAfterOrEqual() != null) {
        // insert at the beginning of the list and remove the last element
        if (request.getPageSize() != null && children.size() == request.getPageSize()) {
          children.remove(children.size() - 1);
        }
        children.add(0, entity);
      } else if (request.getSearchBeforeOrEqual() != null) {
        // insert at the end of the list and remove the first element
        if (request.getPageSize() != null && children.size() == request.getPageSize()) {
          children.remove(0);
        }
        children.add(entity);
      }
    }
  }

  private FlowNodeInstanceResponseDto queryFlowNodeInstances(
      final FlowNodeInstanceQueryDto flowNodeInstanceRequest) {
    return queryFlowNodeInstances(flowNodeInstanceRequest, null);
  }

  private FlowNodeInstanceResponseDto queryFlowNodeInstances(
      final FlowNodeInstanceQueryDto flowNodeInstanceRequest, final String flowNodeInstanceId) {

    final var processInstanceId = flowNodeInstanceRequest.getProcessInstanceId();
    final var parentTreePath = flowNodeInstanceRequest.getTreePath();
    final var level = parentTreePath.split("/").length;

    // Build base query
    final var baseQuery =
        ElasticsearchUtil.constantScoreQuery(termsQuery(PROCESS_INSTANCE_KEY, processInstanceId));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(baseQuery);

    // Build running parents aggregation
    final var runningParentFilter =
        new BoolQuery.Builder()
            .must(termsQuery(LEVEL, level - 1))
            .must(ElasticsearchUtil.prefixQuery(TREE_PATH, parentTreePath))
            .mustNot(ElasticsearchUtil.existsQuery(END_DATE))
            .build()
            ._toQuery();

    final var runningParentsAgg =
        new Aggregation.Builder().filter(f -> f.bool(runningParentFilter.bool())).build();

    // Build post filter
    final var postFilterQueries = new java.util.ArrayList<Query>();
    postFilterQueries.add(termsQuery(LEVEL, level));
    postFilterQueries.add(ElasticsearchUtil.prefixQuery(TREE_PATH, parentTreePath));

    if (flowNodeInstanceId != null) {
      postFilterQueries.add(ElasticsearchUtil.idsQuery(flowNodeInstanceId));
    }

    final var postFilter = ElasticsearchUtil.joinWithAnd(postFilterQueries.toArray(new Query[0]));

    // Build search request
    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(whereToSearch(flowNodeInstanceTemplate, ALL))
            .query(tenantAwareQuery)
            .aggregations(AGG_RUNNING_PARENT, runningParentsAgg)
            .postFilter(postFilter);

    if (flowNodeInstanceRequest.getPageSize() != null) {
      searchRequestBuilder.size(flowNodeInstanceRequest.getPageSize());
    }

    applySorting(searchRequestBuilder, flowNodeInstanceRequest);

    try {
      final FlowNodeInstanceResponseDto response;
      if (flowNodeInstanceRequest.getPageSize() != null) {
        response = getOnePage(searchRequestBuilder.build(), processInstanceId);
      } else {
        response = scrollAllSearchHits(searchRequestBuilder, processInstanceId);
      }
      // for process instance level, we don't return running flag
      if (level == 1) {
        response.setRunning(null);
      }
      if (flowNodeInstanceRequest.getSearchBefore() != null
          || flowNodeInstanceRequest.getSearchBeforeOrEqual() != null) {
        response.setChildren(List.copyOf(response.getChildren()).reversed());
      }

      return response;
    } catch (final IOException e) {
      final var message =
          String.format(
              "Exception occurred, while obtaining all flow node instances: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private boolean flowNodeInstanceIsRunningOrIsNotMarked(
      final FlowNodeInstanceEntity flowNodeInstance) {
    return flowNodeInstance.getEndDate() == null || !flowNodeInstance.isIncident();
  }

  private Query hasProcessInstanceAsTreePathPrefixAndIsIncident(final String treePath) {
    return joinWithAnd(
        ElasticsearchUtil.prefixQuery(TREE_PATH, treePath),
        ElasticsearchUtil.termsQuery(INCIDENT, true));
  }

  // Max size: page size of request - default: 50
  private void markHasIncident(
      final String processInstanceId, final List<FlowNodeInstanceEntity> flowNodeInstances) {
    if (flowNodeInstances == null || flowNodeInstances.isEmpty()) {
      return;
    }

    final var filters =
        flowNodeInstances.stream()
            .filter(this::flowNodeInstanceIsRunningOrIsNotMarked)
            .collect(
                Collectors.toMap(
                    FlowNodeInstanceEntity::getId,
                    fni -> hasProcessInstanceAsTreePathPrefixAndIsIncident(fni.getTreePath())));

    if (filters.isEmpty()) {
      return;
    }

    final var query = ElasticsearchUtil.termsQuery(PROCESS_INSTANCE_KEY, processInstanceId);
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var request =
        new SearchRequest.Builder()
            .index(whereToSearch(flowNodeInstanceTemplate, ALL))
            .query(tenantAwareQuery)
            .size(0)
            .aggregations(
                NUMBER_OF_INCIDENTS_FOR_TREE_PATH,
                a -> a.filters(f -> f.filters(Buckets.of(b -> b.keyed(filters)))))
            .build();

    try {
      final var response = esClient.search(request, FlowNodeInstanceEntity.class);
      final var filterBuckets =
          response.aggregations().get(NUMBER_OF_INCIDENTS_FOR_TREE_PATH).filters();

      final Map<String, Long> flowNodeIdIncidents = new HashMap<>();
      filterBuckets
          .buckets()
          .keyed()
          .forEach((key, bucket) -> flowNodeIdIncidents.put(key, bucket.docCount()));

      for (final var flowNodeInstance : flowNodeInstances) {
        final Long count = flowNodeIdIncidents.getOrDefault(flowNodeInstance.getId(), 0L);
        if (count > 0) {
          flowNodeInstance.setIncident(true);
        }
      }
    } catch (final IOException e) {
      LOGGER.error("Could not retrieve flow node incidents", e);
    }
  }

  private void applySorting(
      final SearchRequest.Builder searchRequestBuilder, final FlowNodeInstanceQueryDto request) {

    final var directSorting =
        request.getSearchAfter() != null
            || request.getSearchAfterOrEqual() != null
            || (request.getSearchBefore() == null && request.getSearchBeforeOrEqual() == null);

    if (directSorting) { // this sorting is also the default one for 1st page
      searchRequestBuilder
          .sort(ElasticsearchUtil.sortOrder(START_DATE, SortOrder.Asc))
          .sort(ElasticsearchUtil.sortOrder(ID, SortOrder.Asc));
      if (request.getSearchAfter() != null) {
        searchRequestBuilder.searchAfter(
            searchAfterToFieldValues(request.getSearchAfter(objectMapper)));
      } else if (request.getSearchAfterOrEqual() != null) {
        searchRequestBuilder.searchAfter(
            searchAfterToFieldValues(request.getSearchAfterOrEqual(objectMapper)));
      }
    } else { // searchBefore != null
      // reverse sorting
      searchRequestBuilder
          .sort(ElasticsearchUtil.sortOrder(START_DATE, SortOrder.Desc))
          .sort(ElasticsearchUtil.sortOrder(ID, SortOrder.Desc));
      if (request.getSearchBefore() != null) {
        searchRequestBuilder.searchAfter(
            searchAfterToFieldValues(request.getSearchBefore(objectMapper)));
      } else if (request.getSearchBeforeOrEqual() != null) {
        searchRequestBuilder.searchAfter(
            searchAfterToFieldValues(request.getSearchBeforeOrEqual(objectMapper)));
      }
    }
  }

  private FlowNodeInstanceResponseDto scrollAllSearchHits(
      final SearchRequest.Builder searchRequestBuilder, final String processInstanceId)
      throws IOException {
    final var runningParent = new AtomicBoolean();
    final List<FlowNodeInstanceEntity> children =
        ElasticsearchUtil.scrollAllStream(
                esClient, searchRequestBuilder, FlowNodeInstanceEntity.class)
            .flatMap(
                response -> {
                  processAggregation(response.aggregations(), null, runningParent);
                  return response.hits().hits().stream();
                })
            .map(
                hit -> {
                  final var entity = hit.source();
                  entity.setSortValues(hit.sort().stream().map(FieldValue::_get).toArray());
                  return entity;
                })
            .toList();
    markHasIncident(processInstanceId, children);
    return new FlowNodeInstanceResponseDto(
        runningParent.get(), FlowNodeInstanceDto.createFrom(children, objectMapper));
  }

  private FlowNodeInstanceResponseDto getOnePage(
      final SearchRequest searchRequest, final String processInstanceId) throws IOException {
    final var searchResponse = esClient.search(searchRequest, FlowNodeInstanceEntity.class);

    final var runningParent = new AtomicBoolean();
    final Set<String> incidentPaths = new HashSet<>();
    processAggregation(searchResponse.aggregations(), incidentPaths, runningParent);

    final List<FlowNodeInstanceEntity> children =
        searchResponse.hits().hits().stream()
            .map(
                hit -> {
                  final var entity = hit.source();
                  entity.setSortValues(hit.sort().stream().map(FieldValue::_get).toArray());
                  return entity;
                })
            .toList();
    // Additional incident marking via separate query
    markHasIncident(processInstanceId, children);
    return new FlowNodeInstanceResponseDto(
        runningParent.get(), FlowNodeInstanceDto.createFrom(children, objectMapper));
  }

  private void processAggregation(
      final Map<String, Aggregate> aggregations,
      final Set<String> incidentPaths,
      final AtomicBoolean runningParent) {
    if (aggregations == null) {
      return;
    }

    processIncidentPathsAggregation(aggregations, incidentPaths);
    processRunningParentAggregation(aggregations, runningParent);
  }

  private void processIncidentPathsAggregation(
      final Map<String, Aggregate> aggregations, final Set<String> incidentPaths) {
    if (incidentPaths == null) {
      return;
    }

    final var incidentsAgg = aggregations.get(AGG_INCIDENTS);
    if (incidentsAgg == null || !incidentsAgg.isFilter()) {
      return;
    }

    final var subAggs = incidentsAgg.filter().aggregations();
    if (subAggs == null) {
      return;
    }

    final var termsAgg = subAggs.get(AGG_INCIDENT_PATHS);
    if (termsAgg != null && termsAgg.isSterms()) {
      incidentPaths.addAll(
          termsAgg.sterms().buckets().array().stream()
              .map(b -> b.key().stringValue())
              .collect(Collectors.toSet()));
    }
  }

  private void processRunningParentAggregation(
      final Map<String, Aggregate> aggregations, final AtomicBoolean runningParent) {
    final var runningParentAgg = aggregations.get(AGG_RUNNING_PARENT);
    if (runningParentAgg != null
        && runningParentAgg.isFilter()
        && runningParentAgg.filter().docCount() > 0) {
      runningParent.set(true);
    }
  }

  private FlowNodeMetadataDto getMetadataByFlowNodeInstanceId(final String flowNodeInstanceId) {

    final FlowNodeInstanceEntity flowNodeInstance = getFlowNodeInstanceEntity(flowNodeInstanceId);

    final FlowNodeMetadataDto result = new FlowNodeMetadataDto();
    result.setInstanceMetadata(buildInstanceMetadata(flowNodeInstance));
    result.setFlowNodeInstanceId(flowNodeInstanceId);

    // calculate breadcrumb
    result.setBreadcrumb(
        buildBreadcrumb(
            flowNodeInstance.getTreePath(),
            flowNodeInstance.getFlowNodeId(),
            flowNodeInstance.getLevel()));

    // find incidents information
    searchForIncidents(
        result,
        String.valueOf(flowNodeInstance.getProcessInstanceKey()),
        flowNodeInstance.getFlowNodeId(),
        flowNodeInstance.getId(),
        flowNodeInstance.getType());

    return result;
  }

  private void searchForIncidents(
      final FlowNodeMetadataDto flowNodeMetadata,
      final String processInstanceId,
      final String flowNodeId,
      final String flowNodeInstanceId,
      final FlowNodeType flowNodeType) {

    final String treePath = processInstanceReader.getProcessInstanceTreePath(processInstanceId);

    final String incidentTreePath =
        new TreePath(treePath)
            .appendFlowNode(flowNodeId)
            .appendFlowNodeInstance(flowNodeInstanceId)
            .toString();

    final var query =
        ElasticsearchUtil.constantScoreQuery(
            joinWithAnd(
                ElasticsearchUtil.termsQuery(IncidentTemplate.TREE_PATH, incidentTreePath),
                ACTIVE_INCIDENT_QUERY_ES8));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var request =
        new SearchRequest.Builder()
            .index(whereToSearch(incidentTemplate, ONLY_RUNTIME))
            .query(tenantAwareQuery)
            .build();

    try {
      final var response = esClient.search(request, IncidentEntity.class);
      flowNodeMetadata.setIncidentCount(response.hits().total().value());

      if (response.hits().total().value() == 1) {
        final var incidentEntity = response.hits().hits().get(0).source();
        final Map<String, IncidentDataHolder> incData =
            incidentReader.collectFlowNodeDataForPropagatedIncidents(
                List.of(incidentEntity), processInstanceId, treePath);
        DecisionInstanceReferenceDto rootCauseDecision = null;
        if (flowNodeType.equals(FlowNodeType.BUSINESS_RULE_TASK)) {
          rootCauseDecision = findRootCauseDecision(incidentEntity.getFlowNodeInstanceKey());
        }
        final IncidentDto incidentDto =
            IncidentDto.createFrom(
                incidentEntity,
                Map.of(
                    incidentEntity.getProcessDefinitionKey(),
                    processCache.getProcessNameOrBpmnProcessId(
                        incidentEntity.getProcessDefinitionKey(),
                        FALLBACK_PROCESS_DEFINITION_NAME)),
                incData.get(incidentEntity.getId()),
                rootCauseDecision);
        flowNodeMetadata.setIncident(incidentDto);
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining incidents: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private void searchForIncidentsByFlowNodeIdAndType(
      final FlowNodeMetadataDto flowNodeMetadata,
      final String processInstanceId,
      final String flowNodeId,
      final FlowNodeType flowNodeType) {

    final String treePath = processInstanceReader.getProcessInstanceTreePath(processInstanceId);

    final String flowNodeInstancesTreePath =
        new TreePath(treePath).appendFlowNode(flowNodeId).toString();

    final var query =
        ElasticsearchUtil.constantScoreQuery(
            joinWithAnd(
                ElasticsearchUtil.termsQuery(IncidentTemplate.TREE_PATH, flowNodeInstancesTreePath),
                ACTIVE_INCIDENT_QUERY_ES8));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var request =
        new SearchRequest.Builder()
            .index(whereToSearch(incidentTemplate, ONLY_RUNTIME))
            .query(tenantAwareQuery)
            .build();

    try {
      final var response = esClient.search(request, IncidentEntity.class);
      flowNodeMetadata.setIncidentCount(response.hits().total().value());

      if (response.hits().total().value() == 1) {
        final var incidentEntity = response.hits().hits().get(0).source();
        final Map<String, IncidentDataHolder> incData =
            incidentReader.collectFlowNodeDataForPropagatedIncidents(
                List.of(incidentEntity), processInstanceId, treePath);
        DecisionInstanceReferenceDto rootCauseDecision = null;
        if (flowNodeType.equals(FlowNodeType.BUSINESS_RULE_TASK)) {
          rootCauseDecision = findRootCauseDecision(incidentEntity.getFlowNodeInstanceKey());
        }
        final IncidentDto incidentDto =
            IncidentDto.createFrom(
                incidentEntity,
                Map.of(
                    incidentEntity.getProcessDefinitionKey(),
                    processCache.getProcessNameOrBpmnProcessId(
                        incidentEntity.getProcessDefinitionKey(),
                        FALLBACK_PROCESS_DEFINITION_NAME)),
                incData.get(incidentEntity.getId()),
                rootCauseDecision);
        flowNodeMetadata.setIncident(incidentDto);
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining incidents: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private DecisionInstanceReferenceDto findRootCauseDecision(final Long flowNodeInstanceKey) {
    try {
      final var query =
          joinWithAnd(
              ElasticsearchUtil.termsQuery(ELEMENT_INSTANCE_KEY, flowNodeInstanceKey),
              ElasticsearchUtil.termsQuery(
                  DecisionInstanceTemplate.STATE, DecisionInstanceState.FAILED));
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

      final var request =
          new SearchRequest.Builder()
              .index(whereToSearch(decisionInstanceTemplate, ALL))
              .query(tenantAwareQuery)
              .sort(ElasticsearchUtil.sortOrder(EVALUATION_DATE, SortOrder.Desc))
              .size(1)
              .source(s -> s.filter(f -> f.includes(DECISION_NAME, DECISION_ID)))
              .build();

      final var response = esClient.search(request, DecisionInstanceEntity.class);

      if (response.hits().total().value() > 0) {
        final var hit = response.hits().hits().get(0);
        final var source = hit.source();
        String decisionName = source.getDecisionName();
        if (decisionName == null) {
          decisionName = source.getDecisionId();
        }
        return new DecisionInstanceReferenceDto()
            .setDecisionName(decisionName)
            .setInstanceId(hit.id());
      }
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while searching for root cause decision. Flow node instance id: %s. Error message: %s.",
              flowNodeInstanceKey, e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return null;
  }

  private FlowNodeInstanceEntity getFlowNodeInstanceEntity(final String flowNodeInstanceId) {
    try {
      final var query =
          ElasticsearchUtil.constantScoreQuery(
              ElasticsearchUtil.termsQuery(ID, flowNodeInstanceId));
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

      final var request =
          new SearchRequest.Builder()
              .index(whereToSearch(flowNodeInstanceTemplate, ALL))
              .query(tenantAwareQuery)
              .size(1)
              .build();

      final var response = esClient.search(request, FlowNodeInstanceEntity.class);

      if (response.hits().total().value() == 0) {
        throw new OperateRuntimeException("No data found for flow node instance.");
      }

      return response.hits().hits().get(0).source();
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining metadata for flow node instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private List<FlowNodeInstanceBreadcrumbEntryDto> buildBreadcrumb(
      final String treePath, final String flowNodeId, final int level) {

    final List<FlowNodeInstanceBreadcrumbEntryDto> result = new ArrayList<>();

    // adjust to use prefixQuery
    final int lastSeparatorIndex = treePath.lastIndexOf("/");
    final String prefixTreePath =
        lastSeparatorIndex > -1 ? treePath.substring(0, lastSeparatorIndex) : treePath;

    final var query =
        ElasticsearchUtil.constantScoreQuery(
            joinWithAnd(
                ElasticsearchUtil.termsQuery(FLOW_NODE_ID, flowNodeId),
                ElasticsearchUtil.prefixQuery(TREE_PATH, prefixTreePath),
                Query.of(q -> q.range(r -> r.number(n -> n.field(LEVEL).lte((double) level))))));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var request =
        new SearchRequest.Builder()
            .index(whereToSearch(flowNodeInstanceTemplate, ALL))
            .query(tenantAwareQuery)
            .source(s -> s.fetch(false))
            .size(0)
            .aggregations(LEVELS_AGG_NAME, getLevelsAggs())
            .build();

    try {
      final var response = esClient.search(request, FlowNodeInstanceEntity.class);

      final var levelsAggResult = response.aggregations().get(LEVELS_AGG_NAME);
      if (levelsAggResult != null && levelsAggResult.isLterms()) {
        result.addAll(
            buildBreadcrumbForFlowNodeId(levelsAggResult.lterms().buckets().array(), level));
      }

      return result;
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining metadata for flow node: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private FlowNodeMetadataDto getMetadataByFlowNodeId(
      final String processInstanceId, final String flowNodeId, final FlowNodeType flowNodeType) {

    final var query =
        ElasticsearchUtil.constantScoreQuery(
            joinWithAnd(
                ElasticsearchUtil.termsQuery(PROCESS_INSTANCE_KEY, processInstanceId),
                ElasticsearchUtil.termsQuery(FLOW_NODE_ID, flowNodeId)));
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var requestBuilder =
        new SearchRequest.Builder()
            .index(whereToSearch(flowNodeInstanceTemplate, ALL))
            .query(tenantAwareQuery)
            .sort(ElasticsearchUtil.sortOrder(LEVEL, SortOrder.Asc))
            .aggregations(LEVELS_AGG_NAME, getLevelsAggs())
            .size(1);

    if (flowNodeType != null) {
      requestBuilder.postFilter(ElasticsearchUtil.termsQuery(TYPE, flowNodeType));
    }

    final var request = requestBuilder.build();

    try {
      final var response = esClient.search(request, FlowNodeInstanceEntity.class);

      final FlowNodeMetadataDto result = new FlowNodeMetadataDto();

      if (response.hits().total().value() == 0) {
        throw new OperateRuntimeException("No data found for flow node instance.");
      }

      final var flowNodeInstance = response.hits().hits().get(0).source();

      final var levelsAggResult = response.aggregations().get(LEVELS_AGG_NAME);
      if (levelsAggResult != null && levelsAggResult.isLterms()) {
        final var buckets = levelsAggResult.lterms().buckets().array();
        if (!buckets.isEmpty()) {
          final var bucketCurrentLevel = getBucketFromLevel(buckets, flowNodeInstance.getLevel());
          if (bucketCurrentLevel.docCount() == 1) {
            result.setInstanceMetadata(buildInstanceMetadata(flowNodeInstance));
            result.setFlowNodeInstanceId(flowNodeInstance.getId());
            // scenario 1-2
            result.setBreadcrumb(
                buildBreadcrumbForFlowNodeId(buckets, flowNodeInstance.getLevel()));
            // find incidents information
            searchForIncidents(
                result,
                String.valueOf(flowNodeInstance.getProcessInstanceKey()),
                flowNodeInstance.getFlowNodeId(),
                flowNodeInstance.getId(),
                flowNodeInstance.getType());
          } else {
            result.setInstanceCount(bucketCurrentLevel.docCount());
            result.setFlowNodeId(flowNodeInstance.getFlowNodeId());
            result.setFlowNodeType(flowNodeInstance.getType());
            // find incidents information
            searchForIncidentsByFlowNodeIdAndType(
                result,
                String.valueOf(flowNodeInstance.getProcessInstanceKey()),
                flowNodeInstance.getFlowNodeId(),
                flowNodeInstance.getType());
          }
        }
      }

      return result;
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining metadata for flow node: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private Aggregation getLevelsAggs() {
    return Aggregation.of(
        a ->
            a.terms(
                    t ->
                        t.field(LEVEL)
                            .size(TERMS_AGG_SIZE)
                            .order(NamedValue.of("_key", SortOrder.Asc)))
                .aggregations(LEVELS_TOP_HITS_AGG_NAME, sub -> sub.topHits(th -> th.size(1))));
  }

  private FlowNodeInstanceMetadata buildInstanceMetadata(
      final FlowNodeInstanceEntity flowNodeInstance) {
    return flowNodeInstanceMetadataBuilder.buildFrom(flowNodeInstance);
  }

  private Set<String> collectFinishedFlowNodes(final Aggregate finishedFlowNodes) {
    final Set<String> result = new HashSet<>();
    if (finishedFlowNodes != null && finishedFlowNodes.isFilter()) {
      final var subAggs = finishedFlowNodes.filter().aggregations();
      if (subAggs != null) {
        final var termsAgg = subAggs.get(FINISHED_FLOW_NODES_BUCKETS_AGG_NAME);
        if (termsAgg != null && termsAgg.isSterms()) {
          for (final var bucket : termsAgg.sterms().buckets().array()) {
            result.add(bucket.key().stringValue());
          }
        }
      }
    }
    return result;
  }

  private void collectActiveFlowNodeStates(
      final Aggregate activeFlowNodesAgg,
      final Set<String> incidentPaths,
      final Map<String, FlowNodeStateDto> result) {
    if (activeFlowNodesAgg == null || !activeFlowNodesAgg.isFilter()) {
      return;
    }

    final var subAggs = activeFlowNodesAgg.filter().aggregations();
    if (subAggs == null) {
      return;
    }

    final var flowNodesAgg = subAggs.get(ACTIVE_FLOW_NODES_BUCKETS_AGG_NAME);
    if (flowNodesAgg == null || !flowNodesAgg.isSterms()) {
      return;
    }

    for (final var flowNode : flowNodesAgg.sterms().buckets().array()) {
      processFlowNodeBucket(flowNode, incidentPaths, result);
    }
  }

  private void processFlowNodeBucket(
      final StringTermsBucket flowNode,
      final Set<String> incidentPaths,
      final Map<String, FlowNodeStateDto> result) {
    final var latestFlowNodeAgg = flowNode.aggregations().get(LATEST_FLOW_NODE_AGG_NAME);
    if (latestFlowNodeAgg == null || !latestFlowNodeAgg.isTopHits()) {
      return;
    }

    final var topHits = latestFlowNodeAgg.topHits();
    if (topHits.hits().hits().isEmpty()) {
      return;
    }

    final var hit = topHits.hits().hits().get(0);
    if (hit.source() == null) {
      return;
    }

    // Deserialize JsonData to Map
    final Map<String, Object> lastFlowNodeFields =
        objectMapper.convertValue(hit.source(), ElasticsearchUtil.MAP_CLASS);

    final var stateValue =
        Objects.requireNonNull(lastFlowNodeFields.get(STATE), "STATE field must not be null");
    final var treePathValue = lastFlowNodeFields.get(TREE_PATH);

    var flowNodeState = FlowNodeStateDto.valueOf(stateValue.toString());

    if (flowNodeState.equals(FlowNodeStateDto.ACTIVE)
        && treePathValue != null
        && incidentPaths.contains(treePathValue.toString())) {
      flowNodeState = FlowNodeStateDto.INCIDENT;
    }

    result.put(flowNode.key().stringValue(), flowNodeState);
  }

  private LongTermsBucket getBucketFromLevel(final List<LongTermsBucket> buckets, final int level) {
    return buckets.stream()
        .filter(b -> b.key() == level)
        .findFirst()
        .orElseThrow(
            () ->
                new OperateRuntimeException(
                    "No aggregation bucket found for level "
                        + level
                        + " when building breadcrumb"));
  }

  private List<FlowNodeInstanceBreadcrumbEntryDto> buildBreadcrumbForFlowNodeId(
      final List<LongTermsBucket> buckets, final int currentInstanceLevel) {
    if (buckets.isEmpty()) {
      return new ArrayList<>();
    }
    final List<FlowNodeInstanceBreadcrumbEntryDto> breadcrumb = new ArrayList<>();
    final FlowNodeType firstBucketFlowNodeType = getFirstBucketFlowNodeType(buckets);
    if ((firstBucketFlowNodeType != null
            && firstBucketFlowNodeType.equals(FlowNodeType.MULTI_INSTANCE_BODY))
        || getBucketFromLevel(buckets, currentInstanceLevel).docCount() > 1) {
      for (final var levelBucket : buckets) {
        final var levelTopHits = levelBucket.aggregations().get(LEVELS_TOP_HITS_AGG_NAME).topHits();
        final var hit = levelTopHits.hits().hits().get(0);
        final Map<String, Object> instanceFields =
            objectMapper.convertValue(hit.source(), ElasticsearchUtil.MAP_CLASS);
        if ((int) instanceFields.get(LEVEL) <= currentInstanceLevel) {
          final var flowNodeType = FlowNodeType.valueOf((String) instanceFields.get(TYPE));
          final var breadcrumbEntry =
              new FlowNodeInstanceBreadcrumbEntryDto(
                  String.valueOf(instanceFields.get(FLOW_NODE_ID)), flowNodeType);
          breadcrumb.add(breadcrumbEntry);
        }
      }
    }
    return breadcrumb;
  }

  private FlowNodeType getFirstBucketFlowNodeType(final List<LongTermsBucket> buckets) {
    if (buckets.isEmpty()) {
      return null;
    }

    final var topHitsAgg = buckets.get(0).aggregations().get(LEVELS_TOP_HITS_AGG_NAME);
    if (topHitsAgg == null || !topHitsAgg.isTopHits()) {
      return null;
    }

    final var hits = topHitsAgg.topHits().hits().hits();
    if (hits.isEmpty() || hits.get(0).source() == null) {
      return null;
    }

    final Map<String, Object> instanceFields =
        objectMapper.convertValue(hits.get(0).source(), ElasticsearchUtil.MAP_CLASS);
    final var typeValue = (String) instanceFields.get(TYPE);

    return typeValue == null ? null : FlowNodeType.valueOf(typeValue);
  }
}
