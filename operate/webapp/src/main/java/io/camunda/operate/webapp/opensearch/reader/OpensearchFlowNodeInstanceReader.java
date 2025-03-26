/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.OpensearchIncidentStore.ACTIVE_INCIDENT_QUERY;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.filtersAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.termAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.topHitsAggregation;
import static io.camunda.operate.store.opensearch.dsl.AggregationDSL.withSubaggregations;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.*;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;
import static io.camunda.operate.util.ElasticsearchUtil.TERMS_AGG_SIZE;
import static io.camunda.operate.webapp.rest.dto.incidents.IncidentDto.FALLBACK_PROCESS_DEFINITION_NAME;
import static io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex.DECISION_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.DECISION_NAME;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.ELEMENT_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.EVALUATION_DATE;
import static io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate.END_DATE;
import static io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate.ID;
import static io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate.LEVEL;
import static io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate.START_DATE;
import static io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate.TREE_PATH;
import static io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate.TYPE;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.ACTIVE;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.COMPLETED;
import static io.camunda.webapps.schema.entities.flownode.FlowNodeState.TERMINATED;
import static org.opensearch.client.opensearch._types.SortOrder.Asc;
import static org.opensearch.client.opensearch._types.SortOrder.Desc;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.webapp.data.IncidentDataHolder;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.FlowNodeInstanceMetadataBuilder;
import io.camunda.operate.webapp.rest.dto.FlowNodeStatisticsDto;
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
import io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.aggregations.*;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchFlowNodeInstanceReader extends OpensearchAbstractReader
    implements FlowNodeInstanceReader {

  @Autowired
  @Qualifier("operateFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private OpensearchIncidentReader incidentReader;

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private ProcessCache processCache;

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

  @Override
  public Map<String, FlowNodeStateDto> getFlowNodeStates(final String processInstanceId) {
    final String latestFlowNodeAggName = "latestFlowNode";
    final String activeFlowNodesAggName = "activeFlowNodes";
    final String activeFlowNodesBucketsAggName = "activeFlowNodesBuckets";
    final String finishedFlowNodesAggName = "finishedFlowNodes";

    final Query query =
        constantScore(withTenantCheck(term(PROCESS_INSTANCE_KEY, processInstanceId)));

    final Aggregation notCompletedFlowNodesAggs =
        withSubaggregations(
            stringTerms(STATE, List.of(ACTIVE.name(), TERMINATED.name())),
            Map.of(
                activeFlowNodesBucketsAggName,
                withSubaggregations(
                    termAggregation(FLOW_NODE_ID, TERMS_AGG_SIZE),
                    Map.of(
                        latestFlowNodeAggName,
                        topHitsAggregation(
                                List.of(STATE, TREE_PATH), 1, sortOptions(START_DATE, Desc))
                            ._toAggregation()))));

    final Aggregation finishedFlowNodesAggs =
        withSubaggregations(
            term(STATE, COMPLETED.name()),
            Map.of(
                FINISHED_FLOW_NODES_BUCKETS_AGG_NAME,
                termAggregation(FLOW_NODE_ID, TERMS_AGG_SIZE)._toAggregation()));

    final Aggregation incidentsAggs =
        withSubaggregations(
            term(INCIDENT, true),
            Map.of(
                AGG_INCIDENT_PATHS, termAggregation(TREE_PATH, TERMS_AGG_SIZE)._toAggregation()));

    final var searchRequestBuilder =
        searchRequestBuilder(flowNodeInstanceTemplate)
            .query(query)
            .aggregations(
                Map.of(
                    activeFlowNodesAggName, notCompletedFlowNodesAggs,
                    AGG_INCIDENTS, incidentsAggs,
                    finishedFlowNodesAggName, finishedFlowNodesAggs))
            .size(0);

    final Map<String, Aggregate> aggregates =
        richOpenSearchClient.doc().searchAggregations(searchRequestBuilder);

    final Set<String> incidentPaths = getIncidentPaths(aggregates.get(AGG_INCIDENTS).filter());

    final Set<String> finishedFlowNodes =
        collectFinishedFlowNodes(aggregates.get(finishedFlowNodesAggName).filter());

    final StringTermsAggregate flowNodesAgg =
        aggregates
            .get(activeFlowNodesAggName)
            .filter()
            .aggregations()
            .get(activeFlowNodesBucketsAggName)
            .sterms();

    final Map<String, FlowNodeStateDto> result = new HashMap<>();
    if (flowNodesAgg != null) {
      record FlowNodeResult(String state, String treePath) {}
      for (final StringTermsBucket bucket : flowNodesAgg.buckets().array()) {
        final var lastFlowNode =
            bucket
                .aggregations()
                .get(latestFlowNodeAggName)
                .topHits()
                .hits()
                .hits()
                .get(0)
                .source()
                .to(FlowNodeResult.class);

        var flowNodeState = FlowNodeStateDto.valueOf(lastFlowNode.state());
        if (flowNodeState == FlowNodeStateDto.ACTIVE
            && incidentPaths.contains(lastFlowNode.treePath())) {
          flowNodeState = FlowNodeStateDto.INCIDENT;
        }
        result.put(bucket.key(), flowNodeState);
      }
    }

    // add finished when needed
    for (final String finishedFlowNodeId : finishedFlowNodes) {
      if (result.get(finishedFlowNodeId) == null) {
        result.put(finishedFlowNodeId, FlowNodeStateDto.COMPLETED);
      }
    }

    return result;
  }

  @Override
  public List<Long> getFlowNodeInstanceKeysByIdAndStates(
      final Long processInstanceId, final String flowNodeId, final List<FlowNodeState> states) {
    final var searchRequestBuilder =
        searchRequestBuilder(flowNodeInstanceTemplate.getAlias())
            .query(
                withTenantCheck(
                    and(
                        term(FLOW_NODE_ID, flowNodeId),
                        term(PROCESS_INSTANCE_KEY, processInstanceId),
                        stringTerms(STATE, states.stream().map(Enum::name).toList()))))
            .source(sourceInclude(ID));

    record Result(String id) {}

    return richOpenSearchClient.doc().searchValues(searchRequestBuilder, Result.class).stream()
        .map(r -> Long.parseLong(r.id()))
        .toList();
  }

  @Override
  public Collection<FlowNodeStatisticsDto> getFlowNodeStatisticsForProcessInstance(
      final Long processInstanceId) {
    final var searchRequestBuilder =
        searchRequestBuilder(flowNodeInstanceTemplate)
            .query(
                constantScore(
                    withTenantCheck(
                        term(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceId))))
            .aggregations(
                FLOW_NODE_ID_AGG,
                withSubaggregations(
                    termAggregation(FLOW_NODE_ID, TERMS_AGG_SIZE),
                    Map.of(
                        COUNT_INCIDENT, term(INCIDENT, true)._toAggregation(),
                        COUNT_CANCELED,
                            and(
                                    not(term(TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                                    term(STATE, TERMINATED.name()))
                                ._toAggregation(),
                        COUNT_COMPLETED,
                            and(
                                    not(term(TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                                    term(STATE, COMPLETED.name()))
                                ._toAggregation(),
                        COUNT_ACTIVE,
                            and(
                                    not(term(TYPE, FlowNodeType.MULTI_INSTANCE_BODY.name())),
                                    term(STATE, ACTIVE.name()),
                                    term(INCIDENT, false))
                                ._toAggregation())))
            .size(0);
    return richOpenSearchClient
        .doc()
        .searchAggregations(searchRequestBuilder)
        .get(FLOW_NODE_ID_AGG)
        .sterms()
        .buckets()
        .array()
        .stream()
        .map(
            entry ->
                new FlowNodeStatisticsDto()
                    .setActivityId(entry.key())
                    .setCanceled(entry.aggregations().get(COUNT_CANCELED).filter().docCount())
                    .setIncidents(entry.aggregations().get(COUNT_INCIDENT).filter().docCount())
                    .setCompleted(entry.aggregations().get(COUNT_COMPLETED).filter().docCount())
                    .setActive(entry.aggregations().get(COUNT_ACTIVE).filter().docCount()))
        .toList();
  }

  @Override
  public List<FlowNodeInstanceEntity> getAllFlowNodeInstances(final Long processInstanceKey) {
    final var searchRequestBuilder =
        searchRequestBuilder(flowNodeInstanceTemplate)
            .query(
                constantScore(
                    withTenantCheck(
                        term(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey))))
            .sort(sortOptions(FlowNodeInstanceTemplate.POSITION, Asc));

    return richOpenSearchClient
        .doc()
        .scrollValues(searchRequestBuilder, FlowNodeInstanceEntity.class);
  }

  private FlowNodeInstanceResponseDto getFlowNodeInstances(final FlowNodeInstanceQueryDto request) {
    final FlowNodeInstanceResponseDto response = queryFlowNodeInstances(request);
    // query one additional instance
    if (request.getSearchAfterOrEqual() != null || request.getSearchBeforeOrEqual() != null) {
      adjustResponse(response, request);
    }
    return response;
  }

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

    final String processInstanceId = flowNodeInstanceRequest.getProcessInstanceId();
    final String parentTreePath = flowNodeInstanceRequest.getTreePath();
    final int level = parentTreePath.split("/").length;

    final Query idsQuery = flowNodeInstanceId != null ? ids(flowNodeInstanceId) : null;
    final Query query =
        withTenantCheck(constantScore(term(PROCESS_INSTANCE_KEY, processInstanceId)));

    final Aggregation runningParentsAgg =
        and(not(exists(END_DATE)), prefix(TREE_PATH, parentTreePath), term(LEVEL, level - 1))
            ._toAggregation();

    final Query postFilter = and(term(LEVEL, level), prefix(TREE_PATH, parentTreePath), idsQuery);

    final SearchRequest.Builder searchRequestBuilder =
        searchRequestBuilder(flowNodeInstanceTemplate)
            .query(query)
            .aggregations(AGG_RUNNING_PARENT, runningParentsAgg)
            .postFilter(postFilter);

    if (flowNodeInstanceRequest.getPageSize() != null) {
      searchRequestBuilder.size(flowNodeInstanceRequest.getPageSize());
    }

    applySorting(searchRequestBuilder, flowNodeInstanceRequest);

    try {
      final FlowNodeInstanceResponseDto response;
      if (flowNodeInstanceRequest.getPageSize() != null) {
        response = getOnePage(searchRequestBuilder, processInstanceId);
      } else {
        response = scrollAllSearchHits(searchRequestBuilder, processInstanceId);
      }
      // for process instance level, we don't return running flag
      if (level == 1) {
        response.setRunning(null);
      }
      if (flowNodeInstanceRequest.getSearchBefore() != null
          || flowNodeInstanceRequest.getSearchBeforeOrEqual() != null) {
        Collections.reverse(response.getChildren());
      }

      return response;
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining all flow node instances: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private FlowNodeInstanceResponseDto scrollAllSearchHits(
      final SearchRequest.Builder searchRequestBuilder, final String processInstanceId)
      throws IOException {
    final OpenSearchDocumentOperations.AggregatedResult<Hit<FlowNodeInstanceEntity>> response =
        richOpenSearchClient.doc().scrollHits(searchRequestBuilder, FlowNodeInstanceEntity.class);
    final List<FlowNodeInstanceEntity> children =
        response.values().stream()
            .map(
                hit -> {
                  final var entity = hit.source();
                  entity.setSortValues(hit.sort().toArray());
                  return entity;
                })
            .toList();

    final boolean runningParent = isRunningParent(response.aggregates());

    markHasIncident(processInstanceId, children);
    return new FlowNodeInstanceResponseDto(
        runningParent, FlowNodeInstanceDto.createFrom(children, objectMapper));
  }

  private void applySorting(
      final SearchRequest.Builder searchRequestBuilder, final FlowNodeInstanceQueryDto request) {
    final boolean directSorting =
        request.getSearchAfter() != null
            || request.getSearchAfterOrEqual() != null
            || (request.getSearchBefore() == null && request.getSearchBeforeOrEqual() == null);

    final Function<Object[], List<String>> toStrings =
        objects -> Arrays.stream(objects).map(Object::toString).toList();

    if (directSorting) { // this sorting is also the default one for 1st page
      searchRequestBuilder.sort(sortOptions(START_DATE, Asc), sortOptions(ID, Asc));
      if (request.getSearchAfter() != null) {
        searchRequestBuilder.searchAfter(toStrings.apply(request.getSearchAfter(objectMapper)));
      } else if (request.getSearchAfterOrEqual() != null) {
        searchRequestBuilder.searchAfter(
            toStrings.apply(request.getSearchAfterOrEqual(objectMapper)));
      }
    } else { // searchBefore != null
      // reverse sorting
      searchRequestBuilder.sort(sortOptions(START_DATE, Desc), sortOptions(ID, Desc));
      if (request.getSearchBefore() != null) {
        searchRequestBuilder.searchAfter(toStrings.apply(request.getSearchBefore(objectMapper)));
      } else if (request.getSearchBeforeOrEqual() != null) {
        searchRequestBuilder.searchAfter(
            toStrings.apply(request.getSearchBeforeOrEqual(objectMapper)));
      }
    }
  }

  private FlowNodeInstanceResponseDto getOnePage(
      final SearchRequest.Builder searchRequestBuilder, final String processInstanceId)
      throws IOException {
    final var response =
        richOpenSearchClient.doc().search(searchRequestBuilder, FlowNodeInstanceEntity.class);
    final boolean runningParent = isRunningParent(response.aggregations());

    final List<FlowNodeInstanceEntity> children =
        response.hits().hits().stream()
            .map(
                hit -> {
                  final var entity = hit.source();
                  entity.setSortValues(hit.sort().toArray());
                  return entity;
                })
            .toList();

    markHasIncident(processInstanceId, children);

    return new FlowNodeInstanceResponseDto(
        runningParent, FlowNodeInstanceDto.createFrom(children, objectMapper));
  }

  private void markHasIncident(
      final String processInstanceId, final List<FlowNodeInstanceEntity> flowNodeInstances) {
    if (flowNodeInstances == null || flowNodeInstances.isEmpty()) {
      return;
    }
    final Map<String, Query> filters =
        flowNodeInstances.stream()
            .filter(this::flowNodeInstanceIsRunningOrIsNotMarked)
            .collect(
                Collectors.toMap(
                    flowNodeInstance -> flowNodeInstance.getId(),
                    flowNodeInstance ->
                        and(
                            prefix(TREE_PATH, flowNodeInstance.getTreePath()),
                            term(INCIDENT, true))));

    final var searchRequestBuilder =
        searchRequestBuilder(flowNodeInstanceTemplate)
            .query(withTenantCheck(term(PROCESS_INSTANCE_KEY, processInstanceId)))
            .size(0)
            .aggregations(
                NUMBER_OF_INCIDENTS_FOR_TREE_PATH, filtersAggregation(filters)._toAggregation());

    final Map<String, Long> flowNodeIdIncidents =
        richOpenSearchClient
            .doc()
            .searchAggregations(searchRequestBuilder)
            .get(NUMBER_OF_INCIDENTS_FOR_TREE_PATH)
            .filters()
            .buckets()
            .keyed()
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue().docCount()));

    for (final var flowNodeInstance : flowNodeInstances) {
      final Long count = flowNodeIdIncidents.getOrDefault(flowNodeInstance.getId(), 0L);
      if (count > 0) {
        flowNodeInstance.setIncident(true);
      }
    }
  }

  private boolean flowNodeInstanceIsRunningOrIsNotMarked(
      final FlowNodeInstanceEntity flowNodeInstance) {
    return flowNodeInstance.getEndDate() == null || !flowNodeInstance.isIncident();
  }

  private boolean isRunningParent(final Map<String, Aggregate> aggs) {
    final Aggregate filterAggs = aggs.get(AGG_RUNNING_PARENT);
    return filterAggs != null && filterAggs.filter().docCount() > 0;
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

  private List<FlowNodeInstanceBreadcrumbEntryDto> buildBreadcrumb(
      final String treePath, final String flowNodeId, final int level) {
    // adjust to use prefixQuery
    final int lastSeparatorIndex = treePath.lastIndexOf("/");
    final String prefixTreePath =
        lastSeparatorIndex > -1 ? treePath.substring(0, lastSeparatorIndex) : treePath;

    final var searchRequestBuilder =
        searchRequestBuilder(flowNodeInstanceTemplate)
            .query(
                constantScore(
                    withTenantCheck(
                        and(
                            term(FLOW_NODE_ID, flowNodeId),
                            prefix(TREE_PATH, prefixTreePath),
                            lte(LEVEL, level)))))
            .source(s -> s.fetch(false))
            .size(0)
            .aggregations(LEVELS_AGG_NAME, getLevelsAggs());

    final Buckets<LongTermsBucket> buckets =
        richOpenSearchClient
            .doc()
            .searchAggregations(searchRequestBuilder)
            .get(LEVELS_AGG_NAME)
            .lterms()
            .buckets();

    return buildBreadcrumbForFlowNodeId(buckets, level);
  }

  private FlowNodeInstanceEntity getFlowNodeInstanceEntity(final String flowNodeInstanceId) {
    final var searchRequestBuilder =
        searchRequestBuilder(flowNodeInstanceTemplate)
            .query(constantScore(withTenantCheck(term(ID, flowNodeInstanceId))));

    final List<Hit<FlowNodeInstanceEntity>> hits =
        richOpenSearchClient
            .doc()
            .search(searchRequestBuilder, FlowNodeInstanceEntity.class)
            .hits()
            .hits();

    if (hits.isEmpty()) {
      throw new OperateRuntimeException("No data found for flow node instance.");
    }

    return hits.get(0).source();
  }

  private FlowNodeMetadataDto getMetadataByFlowNodeId(
      final String processInstanceId, final String flowNodeId, final FlowNodeType flowNodeType) {
    final var searchRequestBuilder =
        searchRequestBuilder(flowNodeInstanceTemplate)
            .query(
                constantScore(
                    withTenantCheck(
                        and(
                            term(PROCESS_INSTANCE_KEY, processInstanceId),
                            term(FLOW_NODE_ID, flowNodeId)))))
            .sort(sortOptions(LEVEL, Asc))
            .aggregations(LEVELS_AGG_NAME, getLevelsAggs())
            .size(1);

    if (flowNodeType != null) {
      searchRequestBuilder.postFilter(term(TYPE, flowNodeType.name()));
    }

    final var response =
        richOpenSearchClient.doc().search(searchRequestBuilder, FlowNodeInstanceEntity.class);

    if (response.hits().hits().isEmpty()) {
      throw new OperateRuntimeException("No data found for flow node instance.");
    }

    final FlowNodeMetadataDto result = new FlowNodeMetadataDto();
    final FlowNodeInstanceEntity flowNodeInstance = response.hits().hits().get(0).source();
    final var levelsAgg = response.aggregations().get(LEVELS_AGG_NAME).lterms();

    if (levelsAgg != null
        && levelsAgg.buckets() != null
        && !levelsAgg.buckets().array().isEmpty()) {
      final var bucketCurrentLevel =
          getBucketFromLevel(levelsAgg.buckets(), flowNodeInstance.getLevel());
      if (bucketCurrentLevel.docCount() == 1) {
        result.setInstanceMetadata(buildInstanceMetadata(flowNodeInstance));
        result.setFlowNodeInstanceId(flowNodeInstance.getId());
        // scenario 1-2
        result.setBreadcrumb(
            buildBreadcrumbForFlowNodeId(levelsAgg.buckets(), flowNodeInstance.getLevel()));
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

    return result;
  }

  private LongTermsBucket getBucketFromLevel(
      final Buckets<LongTermsBucket> buckets, final int level) {
    return buckets.array().stream()
        .filter(b -> Integer.valueOf(b.key()).intValue() == level)
        .findFirst()
        .get();
  }

  private void searchForIncidentsByFlowNodeIdAndType(
      final FlowNodeMetadataDto flowNodeMetadata,
      final String processInstanceId,
      final String flowNodeId,
      final FlowNodeType flowNodeType) {
    final String treePath = processInstanceReader.getProcessInstanceTreePath(processInstanceId);
    final String flowNodeInstancesTreePath =
        new TreePath(treePath).appendFlowNode(flowNodeId).toString();
    final var searchRequestBuilder =
        searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
            .query(
                constantScore(
                    withTenantCheck(
                        and(
                            ACTIVE_INCIDENT_QUERY,
                            term(IncidentTemplate.TREE_PATH, flowNodeInstancesTreePath)))));

    final var hitsMeta =
        richOpenSearchClient.doc().search(searchRequestBuilder, IncidentEntity.class).hits();

    flowNodeMetadata.setIncidentCount(hitsMeta.total().value());

    if (hitsMeta.total().value() == 1) {
      final IncidentEntity incidentEntity = hitsMeta.hits().get(0).source();
      final Map<String, IncidentDataHolder> incData =
          incidentReader.collectFlowNodeDataForPropagatedIncidents(
              List.of(incidentEntity), processInstanceId, treePath);
      final DecisionInstanceReferenceDto rootCauseDecision =
          flowNodeType == FlowNodeType.BUSINESS_RULE_TASK
              ? findRootCauseDecision(incidentEntity.getFlowNodeInstanceKey())
              : null;
      final IncidentDto incidentDto =
          IncidentDto.createFrom(
              incidentEntity,
              Map.of(
                  incidentEntity.getProcessDefinitionKey(),
                  processCache.getProcessNameOrBpmnProcessId(
                      incidentEntity.getProcessDefinitionKey(), FALLBACK_PROCESS_DEFINITION_NAME)),
              incData.get(incidentEntity.getId()),
              rootCauseDecision);

      flowNodeMetadata.setIncident(incidentDto);
    }
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

    final var searchRequestBuilder =
        searchRequestBuilder(incidentTemplate, ONLY_RUNTIME)
            .query(
                constantScore(
                    withTenantCheck(
                        and(
                            ACTIVE_INCIDENT_QUERY,
                            term(IncidentTemplate.TREE_PATH, incidentTreePath)))));

    final var hitsMeta =
        richOpenSearchClient.doc().search(searchRequestBuilder, IncidentEntity.class).hits();

    flowNodeMetadata.setIncidentCount(hitsMeta.total().value());
    if (hitsMeta.total().value() == 1) {
      final IncidentEntity incidentEntity = hitsMeta.hits().get(0).source();
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
                      incidentEntity.getProcessDefinitionKey(), FALLBACK_PROCESS_DEFINITION_NAME)),
              incData.get(incidentEntity.getId()),
              rootCauseDecision);

      flowNodeMetadata.setIncident(incidentDto);
    }
  }

  private DecisionInstanceReferenceDto findRootCauseDecision(final Long flowNodeInstanceKey) {
    final var searchRequestBuilder =
        searchRequestBuilder(decisionInstanceTemplate)
            .query(
                withTenantCheck(
                    and(
                        term(ELEMENT_INSTANCE_KEY, flowNodeInstanceKey),
                        term(DecisionInstanceTemplate.STATE, DecisionInstanceState.FAILED.name()))))
            .sort(sortOptions(EVALUATION_DATE, Desc))
            .size(1)
            .source(sourceInclude(DECISION_NAME, DECISION_ID));

    record Result(String decisionName, String decisionId) {}
    final List<Hit<Result>> hits =
        richOpenSearchClient.doc().search(searchRequestBuilder, Result.class).hits().hits();

    if (hits.isEmpty()) {
      return null;
    } else {
      final Result result = hits.get(0).source();
      final var decisionName =
          result.decisionName() != null ? result.decisionName() : result.decisionId();

      return new DecisionInstanceReferenceDto()
          .setDecisionName(decisionName)
          .setInstanceId(hits.get(0).id());
    }
  }

  private List<FlowNodeInstanceBreadcrumbEntryDto> buildBreadcrumbForFlowNodeId(
      final Buckets<LongTermsBucket> buckets, final int currentInstanceLevel) {
    if (buckets.array().size() == 0) {
      return new ArrayList<>();
    }

    final List<FlowNodeInstanceBreadcrumbEntryDto> breadcrumb = new ArrayList<>();
    final FlowNodeType firstBucketFlowNodeType = getFirstBucketFlowNodeType(buckets);
    if ((firstBucketFlowNodeType != null
            && firstBucketFlowNodeType.equals(FlowNodeType.MULTI_INSTANCE_BODY))
        || getBucketFromLevel(buckets, currentInstanceLevel).docCount() > 1) {
      for (final LongTermsBucket levelBucket : buckets.array()) {
        final TopHitsAggregate levelTopHits =
            levelBucket.aggregations().get(LEVELS_TOP_HITS_AGG_NAME).topHits();
        record Result(Integer level, String flowNodeId, String type) {}
        final Result result = levelTopHits.hits().hits().get(0).source().to(Result.class);
        if (result.level <= currentInstanceLevel) {
          breadcrumb.add(
              new FlowNodeInstanceBreadcrumbEntryDto(
                  result.flowNodeId, FlowNodeType.valueOf(result.type)));
        }
      }
    }
    return breadcrumb;
  }

  private FlowNodeType getFirstBucketFlowNodeType(final Buckets<LongTermsBucket> buckets) {
    final var topHits =
        buckets.array().get(0).aggregations().get(LEVELS_TOP_HITS_AGG_NAME).topHits();
    if (topHits != null && topHits.hits().total().value() > 0) {
      record Result(String type) {}
      final Result result = topHits.hits().hits().get(0).source().to(Result.class);
      if (result.type != null) {
        return FlowNodeType.valueOf(result.type);
      }
    }
    return null;
  }

  private FlowNodeInstanceMetadata buildInstanceMetadata(
      final FlowNodeInstanceEntity flowNodeInstance) {
    return flowNodeInstanceMetadataBuilder.buildFrom(flowNodeInstance);
  }

  private Aggregation getLevelsAggs() {
    return withSubaggregations(
        termAggregation(LEVEL, TERMS_AGG_SIZE, Map.of("_key", Asc)), // upper level first
        Map.of(
            LEVELS_TOP_HITS_AGG_NAME,
            topHitsAggregation(1)._toAggregation()) // select one instance per each level
        );
  }

  private Set<String> collectFinishedFlowNodes(final FilterAggregate filterAggs) {
    final Buckets<StringTermsBucket> buckets =
        filterAggs.aggregations().get(FINISHED_FLOW_NODES_BUCKETS_AGG_NAME).sterms().buckets();

    if (buckets != null) {
      return buckets.array().stream().map(b -> b.key()).collect(Collectors.toSet());
    }

    return new HashSet<>();
  }

  private Set<String> getIncidentPaths(final FilterAggregate filterAggs) {
    if (filterAggs != null) {
      final StringTermsAggregate termsAggs =
          filterAggs.aggregations().get(AGG_INCIDENT_PATHS).sterms();
      if (termsAggs != null) {
        return termsAggs.buckets().array().stream().map(b -> b.key()).collect(Collectors.toSet());
      }
    }

    return new HashSet<>();
  }
}
