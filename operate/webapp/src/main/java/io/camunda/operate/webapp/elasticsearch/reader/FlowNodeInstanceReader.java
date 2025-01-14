/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.reader;

import static io.camunda.operate.store.elasticsearch.ElasticsearchIncidentStore.ACTIVE_INCIDENT_QUERY;
import static io.camunda.operate.util.ElasticsearchUtil.TERMS_AGG_SIZE;
import static io.camunda.operate.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
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
import static io.camunda.webapps.schema.entities.operate.FlowNodeState.ACTIVE;
import static io.camunda.webapps.schema.entities.operate.FlowNodeState.COMPLETED;
import static io.camunda.webapps.schema.entities.operate.FlowNodeState.TERMINATED;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.util.ElasticsearchUtil.QueryType;
import io.camunda.operate.webapp.data.IncidentDataHolder;
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
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeState;
import io.camunda.webapps.schema.entities.operate.FlowNodeType;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.document.DocumentField;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.filter.Filters;
import org.elasticsearch.search.aggregations.bucket.filter.FiltersAggregator;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class FlowNodeInstanceReader extends AbstractReader
    implements io.camunda.operate.webapp.reader.FlowNodeInstanceReader {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlowNodeInstanceReader.class);

  @Autowired
  @Qualifier("operateFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

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
    final String latestFlowNodeAggName = "latestFlowNode";
    final String activeFlowNodesAggName = "activeFlowNodes";
    final String activeFlowNodesBucketsAggName = "activeFlowNodesBuckets";
    final String finishedFlowNodesAggName = "finishedFlowNodes";

    final ConstantScoreQueryBuilder query =
        constantScoreQuery(termQuery(PROCESS_INSTANCE_KEY, processInstanceId));

    final AggregationBuilder notCompletedFlowNodesAggs =
        filter(activeFlowNodesAggName, termsQuery(STATE, ACTIVE.name(), TERMINATED.name()))
            .subAggregation(
                terms(activeFlowNodesBucketsAggName)
                    .field(FLOW_NODE_ID)
                    .size(TERMS_AGG_SIZE)
                    .subAggregation(
                        topHits(latestFlowNodeAggName)
                            .sort(START_DATE, SortOrder.DESC)
                            .size(1)
                            .fetchSource(new String[] {STATE, TREE_PATH}, null)));

    final AggregationBuilder finishedFlowNodesAggs =
        filter(finishedFlowNodesAggName, termQuery(STATE, COMPLETED))
            .subAggregation(
                terms(FINISHED_FLOW_NODES_BUCKETS_AGG_NAME)
                    .field(FLOW_NODE_ID)
                    .size(TERMS_AGG_SIZE));

    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(query)
                    .aggregation(notCompletedFlowNodesAggs)
                    .aggregation(getIncidentsAgg())
                    .aggregation(finishedFlowNodesAggs)
                    .size(0));
    try {
      final SearchResponse response = tenantAwareClient.search(request);

      final Set<String> incidentPaths = new HashSet<>();
      processAggregation(response.getAggregations(), incidentPaths, new Boolean[] {false});

      final Set<String> finishedFlowNodes =
          collectFinishedFlowNodes(response.getAggregations().get(finishedFlowNodesAggName));

      final Filter activeFlowNodesAgg = response.getAggregations().get(activeFlowNodesAggName);
      final Terms flowNodesAgg =
          activeFlowNodesAgg.getAggregations().get(activeFlowNodesBucketsAggName);
      final Map<String, FlowNodeStateDto> result = new HashMap<>();
      if (flowNodesAgg != null) {
        for (final Bucket flowNode : flowNodesAgg.getBuckets()) {
          final Map<String, Object> lastFlowNodeFields =
              ((TopHits) flowNode.getAggregations().get(latestFlowNodeAggName))
                  .getHits()
                  .getAt(0)
                  .getSourceAsMap();
          FlowNodeStateDto flowNodeState =
              FlowNodeStateDto.valueOf(lastFlowNodeFields.get(STATE).toString());
          if (flowNodeState.equals(FlowNodeStateDto.ACTIVE)
              && incidentPaths.contains(lastFlowNodeFields.get(TREE_PATH))) {
            flowNodeState = FlowNodeStateDto.INCIDENT;
          }
          result.put(flowNode.getKeyAsString(), flowNodeState);
        }
      }
      // add finished when needed
      for (final String finishedFlowNodeId : finishedFlowNodes) {
        if (result.get(finishedFlowNodeId) == null) {
          result.put(finishedFlowNodeId, FlowNodeStateDto.COMPLETED);
        }
      }
      return result;
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining states for instance flow nodes: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<Long> getFlowNodeInstanceKeysByIdAndStates(
      final Long processInstanceId, final String flowNodeId, final List<FlowNodeState> states) {
    final List<Long> flowNodeInstanceKeys = new ArrayList<>();
    try {
      final SearchRequest searchRequest =
          new SearchRequest(flowNodeInstanceTemplate.getAlias())
              .source(
                  new SearchSourceBuilder()
                      .query(
                          boolQuery()
                              .must(termQuery(FLOW_NODE_ID, flowNodeId))
                              .must(termQuery(PROCESS_INSTANCE_KEY, processInstanceId))
                              .must(
                                  termsQuery(
                                      STATE,
                                      states.stream()
                                          .map(Enum::name)
                                          .collect(Collectors.toList()))))
                      .fetchField(ID));
      final SearchHits searchHits = tenantAwareClient.search(searchRequest).getHits();

      for (final SearchHit searchHit : searchHits) {
        final Map<String, DocumentField> documentFields = searchHit.getDocumentFields();
        flowNodeInstanceKeys.add(Long.parseLong(documentFields.get(ID).getValue()));
      }
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          String.format("Could not retrieve flowNodeInstanceKey for flowNodeId %s ", flowNodeId),
          e);
    }
    return flowNodeInstanceKeys;
  }

  @Override
  public Collection<FlowNodeStatisticsDto> getFlowNodeStatisticsForProcessInstance(
      final Long processInstanceId) {
    try {
      final SearchRequest request =
          ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
              .source(
                  new SearchSourceBuilder()
                      .query(
                          constantScoreQuery(
                              termQuery(
                                  FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY,
                                  processInstanceId)))
                      .aggregation(
                          terms(FLOW_NODE_ID_AGG)
                              .field(FLOW_NODE_ID)
                              .size(TERMS_AGG_SIZE)
                              .subAggregation(
                                  filter(
                                      COUNT_INCIDENT,
                                      boolQuery()
                                          // Need to count when MULTI_INSTANCE_BODY itself has an
                                          // incident
                                          // .mustNot(termQuery(TYPE,
                                          // FlowNodeType.MULTI_INSTANCE_BODY))
                                          .must(termQuery(INCIDENT, true))))
                              .subAggregation(
                                  filter(
                                      COUNT_CANCELED,
                                      boolQuery()
                                          .mustNot(
                                              termQuery(TYPE, FlowNodeType.MULTI_INSTANCE_BODY))
                                          .must(termQuery(STATE, TERMINATED))))
                              .subAggregation(
                                  filter(
                                      COUNT_COMPLETED,
                                      boolQuery()
                                          .mustNot(
                                              termQuery(TYPE, FlowNodeType.MULTI_INSTANCE_BODY))
                                          .must(termQuery(STATE, COMPLETED))))
                              .subAggregation(
                                  filter(
                                      COUNT_ACTIVE,
                                      boolQuery()
                                          .mustNot(
                                              termQuery(TYPE, FlowNodeType.MULTI_INSTANCE_BODY))
                                          .must(termQuery(STATE, ACTIVE))
                                          .must(termQuery(INCIDENT, false)))))
                      .size(0));
      final SearchResponse response = tenantAwareClient.search(request);
      final Aggregations aggregations = response.getAggregations();
      final Terms flowNodeAgg = aggregations.get(FLOW_NODE_ID_AGG);
      return flowNodeAgg.getBuckets().stream()
          .map(
              bucket ->
                  new FlowNodeStatisticsDto()
                      .setActivityId(bucket.getKeyAsString())
                      .setCanceled(
                          ((Filter) bucket.getAggregations().get(COUNT_CANCELED)).getDocCount())
                      .setIncidents(
                          ((Filter) bucket.getAggregations().get(COUNT_INCIDENT)).getDocCount())
                      .setCompleted(
                          ((Filter) bucket.getAggregations().get(COUNT_COMPLETED)).getDocCount())
                      .setActive(
                          ((Filter) bucket.getAggregations().get(COUNT_ACTIVE)).getDocCount()))
          .collect(Collectors.toList());
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining statistics for process instance flow nodes: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  @Override
  public List<FlowNodeInstanceEntity> getAllFlowNodeInstances(final Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery =
        termQuery(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(processInstanceKeyQuery))
                    .sort(FlowNodeInstanceTemplate.POSITION, SortOrder.ASC));
    try {
      return ElasticsearchUtil.scroll(
          searchRequest, FlowNodeInstanceEntity.class, objectMapper, esClient);
    } catch (final IOException e) {
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

    final String processInstanceId = flowNodeInstanceRequest.getProcessInstanceId();
    final String parentTreePath = flowNodeInstanceRequest.getTreePath();
    final int level = parentTreePath.split("/").length;

    IdsQueryBuilder idsQuery = null;
    if (flowNodeInstanceId != null) {
      idsQuery = idsQuery().addIds(flowNodeInstanceId);
    }

    final QueryBuilder query =
        constantScoreQuery(termQuery(PROCESS_INSTANCE_KEY, processInstanceId));

    final AggregationBuilder runningParentsAgg =
        filter(
            AGG_RUNNING_PARENT,
            joinWithAnd(
                boolQuery().mustNot(existsQuery(END_DATE)),
                prefixQuery(TREE_PATH, parentTreePath),
                termQuery(LEVEL, level - 1)));

    final QueryBuilder postFilter =
        joinWithAnd(termQuery(LEVEL, level), prefixQuery(TREE_PATH, parentTreePath), idsQuery);

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(query)
            .aggregation(runningParentsAgg)
            .postFilter(postFilter);
    if (flowNodeInstanceRequest.getPageSize() != null) {
      searchSourceBuilder.size(flowNodeInstanceRequest.getPageSize());
    }

    applySorting(searchSourceBuilder, flowNodeInstanceRequest);

    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate).source(searchSourceBuilder);
    try {
      final FlowNodeInstanceResponseDto response;
      if (flowNodeInstanceRequest.getPageSize() != null) {
        response = getOnePage(searchRequest, processInstanceId);
      } else {
        response = scrollAllSearchHits(searchRequest, processInstanceId);
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

  private AggregationBuilder getIncidentsAgg() {
    return filter(AGG_INCIDENTS, termQuery(INCIDENT, true))
        .subAggregation(terms(AGG_INCIDENT_PATHS).field(TREE_PATH).size(TERMS_AGG_SIZE));
  }

  private FlowNodeInstanceResponseDto scrollAllSearchHits(
      final SearchRequest searchRequest, final String processInstanceId) throws IOException {
    final Boolean[] runningParent = new Boolean[] {false};
    final List<FlowNodeInstanceEntity> children =
        tenantAwareClient.search(
            searchRequest,
            () -> {
              return ElasticsearchUtil.scroll(
                  searchRequest,
                  FlowNodeInstanceEntity.class,
                  objectMapper,
                  esClient,
                  getSearchHitFunction(null),
                  null,
                  getAggsProcessor(null, runningParent));
            });
    markHasIncident(processInstanceId, children);
    return new FlowNodeInstanceResponseDto(
        runningParent[0], FlowNodeInstanceDto.createFrom(children, objectMapper));
  }

  private Function<SearchHit, FlowNodeInstanceEntity> getSearchHitFunction(
      final Set<String> incidentPaths) {
    return (sh) -> {
      final FlowNodeInstanceEntity entity =
          fromSearchHit(sh.getSourceAsString(), objectMapper, FlowNodeInstanceEntity.class);
      entity.setSortValues(sh.getSortValues());
      if (incidentPaths != null && incidentPaths.contains(entity.getTreePath())) {
        entity.setIncident(true);
      }
      return entity;
    };
  }

  private FlowNodeInstanceResponseDto getOnePage(
      final SearchRequest searchRequest, final String processInstanceId) throws IOException {
    final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);

    final Boolean[] runningParent = new Boolean[1];
    processAggregation(searchResponse.getAggregations(), null, runningParent);
    final List<FlowNodeInstanceEntity> children =
        ElasticsearchUtil.mapSearchHits(
            searchResponse.getHits().getHits(), getSearchHitFunction(null));
    markHasIncident(processInstanceId, children);
    return new FlowNodeInstanceResponseDto(
        runningParent[0], FlowNodeInstanceDto.createFrom(children, objectMapper));
  }

  private boolean flowNodeInstanceIsRunningOrIsNotMarked(
      final FlowNodeInstanceEntity flowNodeInstance) {
    return flowNodeInstance.getEndDate() == null || !flowNodeInstance.isIncident();
  }

  private QueryBuilder hasProcessInstanceAsTreePathPrefixAndIsIncident(final String treePath) {
    return joinWithAnd(prefixQuery(TREE_PATH, treePath), termQuery(INCIDENT, true));
  }

  private FiltersAggregator.KeyedFilter newFilterForFlowNodeInstance(
      final FlowNodeInstanceEntity flowNodeInstance) {
    return new FiltersAggregator.KeyedFilter(
        flowNodeInstance.getId(),
        hasProcessInstanceAsTreePathPrefixAndIsIncident(flowNodeInstance.getTreePath()));
  }

  // Max size: page size of request - default: 50
  private void markHasIncident(
      final String processInstanceId, final List<FlowNodeInstanceEntity> flowNodeInstances) {
    if (flowNodeInstances == null || flowNodeInstances.isEmpty()) {
      return;
    }
    final List<FiltersAggregator.KeyedFilter> filters =
        flowNodeInstances.stream()
            .filter(this::flowNodeInstanceIsRunningOrIsNotMarked)
            .map(this::newFilterForFlowNodeInstance)
            .toList();

    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(termQuery(PROCESS_INSTANCE_KEY, processInstanceId))
                    .size(0)
                    .aggregation(
                        AggregationBuilders.filters(
                            NUMBER_OF_INCIDENTS_FOR_TREE_PATH,
                            filters.toArray(new FiltersAggregator.KeyedFilter[0]))));
    try {
      final Map<String, Long> flowNodeIdIncidents = new HashMap<>();
      final SearchResponse response = tenantAwareClient.search(request);
      final Filters filterBuckets =
          response.getAggregations().get(NUMBER_OF_INCIDENTS_FOR_TREE_PATH);

      filterBuckets
          .getBuckets()
          .forEach(b -> flowNodeIdIncidents.put(b.getKeyAsString(), b.getDocCount()));
      // logger.info("FlowNodeInstance -> incidents: {}", flowNodeIdIncidents);
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

  private Consumer<Aggregations> getAggsProcessor(
      final Set<String> incidentPaths, final Boolean[] runningParent) {
    return (aggs) -> {
      if (incidentPaths != null) {
        final Filter filterAggs = aggs.get(AGG_INCIDENTS);
        if (filterAggs != null) {
          final Terms termsAggs = filterAggs.getAggregations().get(AGG_INCIDENT_PATHS);
          if (termsAggs != null) {
            incidentPaths.addAll(
                termsAggs.getBuckets().stream()
                    .map((b) -> b.getKeyAsString())
                    .collect(Collectors.toSet()));
          }
        }
      }
      final Filter filterAggs = aggs.get(AGG_RUNNING_PARENT);
      if (filterAggs != null && filterAggs.getDocCount() > 0) {
        runningParent[0] = true;
      }
    };
  }

  private Set<String> processAggregation(
      final Aggregations aggregations,
      final Set<String> incidentPaths,
      final Boolean[] runningParent) {
    getAggsProcessor(incidentPaths, runningParent).accept(aggregations);
    return incidentPaths;
  }

  /**
   * In case of searchAfterOrEqual and searchBeforeOrEqual, this method will ignore "orEqual" part.
   *
   * @param searchSourceBuilder
   * @param request
   */
  private void applySorting(
      final SearchSourceBuilder searchSourceBuilder, final FlowNodeInstanceQueryDto request) {

    final boolean directSorting =
        request.getSearchAfter() != null
            || request.getSearchAfterOrEqual() != null
            || (request.getSearchBefore() == null && request.getSearchBeforeOrEqual() == null);

    if (directSorting) { // this sorting is also the default one for 1st page
      searchSourceBuilder.sort(START_DATE, SortOrder.ASC).sort(ID, SortOrder.ASC);
      if (request.getSearchAfter() != null) {
        searchSourceBuilder.searchAfter(request.getSearchAfter(objectMapper));
      } else if (request.getSearchAfterOrEqual() != null) {
        searchSourceBuilder.searchAfter(request.getSearchAfterOrEqual(objectMapper));
      }
    } else { // searchBefore != null
      // reverse sorting
      searchSourceBuilder.sort(START_DATE, SortOrder.DESC).sort(ID, SortOrder.DESC);
      if (request.getSearchBefore() != null) {
        searchSourceBuilder.searchAfter(request.getSearchBefore(objectMapper));
      } else if (request.getSearchBeforeOrEqual() != null) {
        searchSourceBuilder.searchAfter(request.getSearchBeforeOrEqual(objectMapper));
      }
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
    final QueryBuilder query =
        constantScoreQuery(
            joinWithAnd(
                termQuery(IncidentTemplate.TREE_PATH, incidentTreePath), ACTIVE_INCIDENT_QUERY));
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, QueryType.ONLY_RUNTIME)
            .source(new SearchSourceBuilder().query(query));
    try {
      final SearchResponse response = tenantAwareClient.search(request);
      flowNodeMetadata.setIncidentCount(response.getHits().getTotalHits().value);
      if (response.getHits().getTotalHits().value == 1) {
        final IncidentEntity incidentEntity =
            fromSearchHit(
                response.getHits().getAt(0).getSourceAsString(),
                objectMapper,
                IncidentEntity.class);
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

    final QueryBuilder query =
        constantScoreQuery(
            joinWithAnd(
                termQuery(IncidentTemplate.TREE_PATH, flowNodeInstancesTreePath),
                ACTIVE_INCIDENT_QUERY));
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(incidentTemplate, QueryType.ONLY_RUNTIME)
            .source(new SearchSourceBuilder().query(query));
    try {
      final SearchResponse response = tenantAwareClient.search(request);
      flowNodeMetadata.setIncidentCount(response.getHits().getTotalHits().value);
      if (response.getHits().getTotalHits().value == 1) {
        final IncidentEntity incidentEntity =
            fromSearchHit(
                response.getHits().getAt(0).getSourceAsString(),
                objectMapper,
                IncidentEntity.class);
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
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(decisionInstanceTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            termQuery(ELEMENT_INSTANCE_KEY, flowNodeInstanceKey),
                            termQuery(
                                DecisionInstanceTemplate.STATE, DecisionInstanceState.FAILED)))
                    .sort(EVALUATION_DATE, SortOrder.DESC)
                    .size(1)
                    .fetchSource(new String[] {DECISION_NAME, DECISION_ID}, null));
    try {
      final SearchResponse response = tenantAwareClient.search(request);
      if (response.getHits().getTotalHits().value > 0) {
        final Map<String, Object> source = response.getHits().getHits()[0].getSourceAsMap();
        String decisionName = (String) source.get(DECISION_NAME);
        if (decisionName == null) {
          decisionName = (String) source.get(DECISION_ID);
        }
        return new DecisionInstanceReferenceDto()
            .setDecisionName(decisionName)
            .setInstanceId(response.getHits().getHits()[0].getId());
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
    final FlowNodeInstanceEntity flowNodeInstance;
    final TermQueryBuilder flowNodeInstanceIdQ = termQuery(ID, flowNodeInstanceId);
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
            .source(new SearchSourceBuilder().query(constantScoreQuery(flowNodeInstanceIdQ)));
    final SearchResponse response;
    try {
      response = tenantAwareClient.search(request);
      flowNodeInstance = getFlowNodeInstance(response);
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining metadata for flow node instance: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return flowNodeInstance;
  }

  private List<FlowNodeInstanceBreadcrumbEntryDto> buildBreadcrumb(
      final String treePath, final String flowNodeId, final int level) {

    final List<FlowNodeInstanceBreadcrumbEntryDto> result = new ArrayList<>();

    // adjust to use prefixQuery
    final int lastSeparatorIndex = treePath.lastIndexOf("/");
    final String prefixTreePath =
        lastSeparatorIndex > -1 ? treePath.substring(0, lastSeparatorIndex) : treePath;

    final QueryBuilder query =
        joinWithAnd(
            termQuery(FLOW_NODE_ID, flowNodeId),
            prefixQuery(TREE_PATH, prefixTreePath),
            rangeQuery(LEVEL).lte(level));

    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(query))
                    .fetchSource(false)
                    .size(0)
                    .aggregation(getLevelsAggs()));
    try {
      final SearchResponse response = tenantAwareClient.search(request);

      final Terms levelsAgg = response.getAggregations().get(LEVELS_AGG_NAME);
      result.addAll(buildBreadcrumbForFlowNodeId(levelsAgg.getBuckets(), level));

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

    final TermQueryBuilder processInstanceIdQ = termQuery(PROCESS_INSTANCE_KEY, processInstanceId);
    final TermQueryBuilder flowNodeIdQ = termQuery(FLOW_NODE_ID, flowNodeId);

    final SearchSourceBuilder sourceBuilder =
        new SearchSourceBuilder()
            .query(constantScoreQuery(joinWithAnd(processInstanceIdQ, flowNodeIdQ)))
            .sort(LEVEL, SortOrder.ASC)
            .aggregation(getLevelsAggs())
            .size(1);
    if (flowNodeType != null) {
      sourceBuilder.postFilter(termQuery(TYPE, flowNodeType));
    }
    final SearchRequest request =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate).source(sourceBuilder);

    try {
      final SearchResponse response = tenantAwareClient.search(request);

      final FlowNodeMetadataDto result = new FlowNodeMetadataDto();
      final FlowNodeInstanceEntity flowNodeInstance = getFlowNodeInstance(response);

      final Terms levelsAgg = response.getAggregations().get(LEVELS_AGG_NAME);
      if (levelsAgg != null
          && levelsAgg.getBuckets() != null
          && levelsAgg.getBuckets().size() > 0) {
        final Bucket bucketCurrentLevel =
            getBucketFromLevel(levelsAgg.getBuckets(), flowNodeInstance.getLevel());
        if (bucketCurrentLevel.getDocCount() == 1) {
          result.setInstanceMetadata(buildInstanceMetadata(flowNodeInstance));
          result.setFlowNodeInstanceId(flowNodeInstance.getId());
          // scenario 1-2
          result.setBreadcrumb(
              buildBreadcrumbForFlowNodeId(levelsAgg.getBuckets(), flowNodeInstance.getLevel()));
          // find incidents information
          searchForIncidents(
              result,
              String.valueOf(flowNodeInstance.getProcessInstanceKey()),
              flowNodeInstance.getFlowNodeId(),
              flowNodeInstance.getId(),
              flowNodeInstance.getType());
        } else {
          result.setInstanceCount(bucketCurrentLevel.getDocCount());
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
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining metadata for flow node: %s", e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private Bucket getBucketFromLevel(final List<? extends Bucket> buckets, final int level) {
    return buckets.stream().filter(b -> b.getKeyAsNumber().intValue() == level).findFirst().get();
  }

  private TermsAggregationBuilder getLevelsAggs() {
    return terms(LEVELS_AGG_NAME)
        .field(LEVEL)
        .size(TERMS_AGG_SIZE)
        .order(BucketOrder.key(true)) // upper level first
        .subAggregation(
            topHits(LEVELS_TOP_HITS_AGG_NAME) // select one instance per each level
                .size(1));
  }

  private FlowNodeInstanceEntity getFlowNodeInstance(final SearchResponse response) {
    if (response.getHits().getTotalHits().value == 0) {
      throw new OperateRuntimeException("No data found for flow node instance.");
    }
    return fromSearchHit(
        response.getHits().getAt(0).getSourceAsString(),
        objectMapper,
        FlowNodeInstanceEntity.class);
  }

  private List<FlowNodeInstanceBreadcrumbEntryDto> buildBreadcrumbForFlowNodeId(
      final List<? extends Bucket> buckets, final int currentInstanceLevel) {
    if (buckets.size() == 0) {
      return new ArrayList<>();
    }
    final List<FlowNodeInstanceBreadcrumbEntryDto> breadcrumb = new ArrayList<>();
    final FlowNodeType firstBucketFlowNodeType = getFirstBucketFlowNodeType(buckets);
    if ((firstBucketFlowNodeType != null
            && firstBucketFlowNodeType.equals(FlowNodeType.MULTI_INSTANCE_BODY))
        || getBucketFromLevel(buckets, currentInstanceLevel).getDocCount() > 1) {
      for (final Bucket levelBucket : buckets) {
        final TopHits levelTopHits = levelBucket.getAggregations().get(LEVELS_TOP_HITS_AGG_NAME);
        final Map<String, Object> instanceFields = levelTopHits.getHits().getAt(0).getSourceAsMap();
        if ((int) instanceFields.get(LEVEL) <= currentInstanceLevel) {
          breadcrumb.add(
              new FlowNodeInstanceBreadcrumbEntryDto(
                  (String) instanceFields.get(FLOW_NODE_ID),
                  FlowNodeType.valueOf((String) instanceFields.get(TYPE))));
        }
      }
    }
    return breadcrumb;
  }

  private FlowNodeType getFirstBucketFlowNodeType(final List<? extends Bucket> buckets) {
    final TopHits topHits = buckets.get(0).getAggregations().get(LEVELS_TOP_HITS_AGG_NAME);
    if (topHits != null && topHits.getHits().getTotalHits().value > 0) {
      final String type = (String) topHits.getHits().getAt(0).getSourceAsMap().get(TYPE);
      if (type != null) {
        return FlowNodeType.valueOf(type);
      }
    }
    return null;
  }

  private FlowNodeInstanceMetadata buildInstanceMetadata(
      final FlowNodeInstanceEntity flowNodeInstance) {
    return flowNodeInstanceMetadataBuilder.buildFrom(flowNodeInstance);
  }

  private Set<String> collectFinishedFlowNodes(final Filter finishedFlowNodes) {
    final Set<String> result = new HashSet<>();
    final List<? extends Bucket> buckets =
        ((Terms) finishedFlowNodes.getAggregations().get(FINISHED_FLOW_NODES_BUCKETS_AGG_NAME))
            .getBuckets();
    if (buckets != null) {
      for (final Bucket bucket : buckets) {
        result.add(bucket.getKeyAsString());
      }
    }
    return result;
  }
}
