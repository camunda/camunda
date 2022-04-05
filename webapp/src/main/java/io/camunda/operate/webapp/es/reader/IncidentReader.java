/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.reader;

import static io.camunda.operate.schema.templates.IncidentTemplate.ACTIVE_INCIDENT_QUERY;
import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ONLY_RUNTIME;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scrollWith;
import static io.camunda.operate.webapp.rest.dto.incidents.IncidentDto.FALLBACK_PROCESS_DEFINITION_NAME;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentErrorTypeDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentFlowNodeDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.zeebeimport.util.TreePath;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IncidentReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(IncidentReader.class);

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  private OperationReader operationReader;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired
  private ProcessCache processCache;

  public List<IncidentEntity> getAllIncidentsByProcessInstanceKey(Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery = termQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);

    final ConstantScoreQueryBuilder query = constantScoreQuery(
        joinWithAnd(processInstanceKeyQuery, ACTIVE_INCIDENT_QUERY));

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder().query(query).sort(IncidentTemplate.CREATION_TIME, SortOrder.ASC));

    try {
      return scroll(searchRequest, IncidentEntity.class);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Returns map of incident ids per process instance id.
   * @param processInstanceKeys
   * @return
   */
  public Map<Long, List<Long>> getIncidentKeysPerProcessInstance(List<Long> processInstanceKeys) {
    final QueryBuilder processInstanceKeysQuery = constantScoreQuery(
        joinWithAnd(termsQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceKeys),
            ACTIVE_INCIDENT_QUERY));
    final int batchSize = operateProperties.getElasticsearch().getBatchSize();

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .query(processInstanceKeysQuery)
            .fetchSource(IncidentTemplate.PROCESS_INSTANCE_KEY, null)
            .size(batchSize));

    Map<Long, List<Long>> result = new HashMap<>();
    try {
      scrollWith(searchRequest, esClient, searchHits -> {
        for (SearchHit hit : searchHits.getHits()) {
          CollectionUtil.addToMap(result, Long.valueOf(hit.getSourceAsMap().get(IncidentTemplate.PROCESS_INSTANCE_KEY).toString()), Long.valueOf(hit.getId()));
        }
      }, null, null);
      return result;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining all incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public IncidentEntity getIncidentById(Long incidentKey) {
    final IdsQueryBuilder idsQ = idsQuery().addIds(incidentKey.toString());

    final ConstantScoreQueryBuilder query = constantScoreQuery(
        joinWithAnd(idsQ, ACTIVE_INCIDENT_QUERY));

    final SearchRequest searchRequest = ElasticsearchUtil.createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder().query(query));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return ElasticsearchUtil.fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper, IncidentEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String.format("Could not find unique incident with key '%s'.", incidentKey));
      } else {
        throw new NotFoundException(String.format("Could not find incident with key '%s'.", incidentKey));
      }
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incident: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  public IncidentResponseDto getIncidentsByProcessInstanceId(String processInstanceId) {
    //get treePath for process instance
    final String treePath = processInstanceReader.getProcessInstanceTreePath(processInstanceId);

    final TermQueryBuilder processInstanceQuery = termQuery(IncidentTemplate.TREE_PATH, treePath);

    final String errorTypesAggName = "errorTypesAgg";

    final TermsAggregationBuilder errorTypesAgg = terms(errorTypesAggName).field(IncidentTemplate.ERROR_TYPE).size(
        ErrorType.values().length)
        .order(BucketOrder.key(true));

    final SearchRequest searchRequest = ElasticsearchUtil
        .createSearchRequest(incidentTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder()
            .query(constantScoreQuery(joinWithAnd(processInstanceQuery, ACTIVE_INCIDENT_QUERY)))
            .aggregation(errorTypesAgg));

    final IncidentResponseDto incidentResponse = new IncidentResponseDto();
    final Map<Long, String> processNames = new HashMap<>();
    try {
      final List<IncidentEntity> incidents = scroll(searchRequest, IncidentEntity.class,
          aggs -> ((Terms) aggs.get(errorTypesAggName)).getBuckets().forEach(b -> {
            ErrorType errorType = ErrorType.valueOf(b.getKeyAsString());
            incidentResponse.getErrorTypes().add(IncidentErrorTypeDto.createFrom(errorType).setCount((int) b.getDocCount()));
          }));
      incidents.stream().filter(inc -> processNames.get(inc.getProcessDefinitionKey()) == null)
          .forEach(inc -> processNames.put(inc.getProcessDefinitionKey(),
              processCache.getProcessNameOrBpmnProcessId(inc.getProcessDefinitionKey(),
                  FALLBACK_PROCESS_DEFINITION_NAME)));

      final Map<Long, List<OperationEntity>> operations = operationReader.getOperationsPerIncidentKey(processInstanceId);

      final Map<String, IncidentDataHolder> incData = collectFlowNodeDataForPropagatedIncidents(
          incidents, processInstanceId, treePath);

      //collect flow node statistics
      incidentResponse.setFlowNodes(incData.values().stream()
          .collect(Collectors.groupingBy(IncidentDataHolder::getFinalFlowNodeId, Collectors.counting()))
          .entrySet().stream()
          .map(entry -> new IncidentFlowNodeDto(entry.getKey(), entry
              .getValue().intValue())).collect(Collectors.toList()));

      final List<IncidentDto> incidentsDtos = IncidentDto
          .sortDefault(IncidentDto.createFrom(incidents, operations, processNames, incData));
      incidentResponse.setIncidents(incidentsDtos);
      incidentResponse.setCount(incidents.size());

      return incidentResponse;
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining incidents: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  /**
   * Returns map incidentId -> IncidentDataHolder.
   * @param incidents
   * @param processInstanceId
   * @param currentTreePath
   * @return
   */
  public Map<String, IncidentDataHolder> collectFlowNodeDataForPropagatedIncidents(
      final List<IncidentEntity> incidents, String processInstanceId, String currentTreePath) {

    final Set<String> flowNodeInstanceIdsSet = new HashSet<>();
    final Map<String, IncidentDataHolder> incDatas = new HashMap<>();
    for (IncidentEntity inc: incidents) {
      IncidentDataHolder incData = new IncidentDataHolder().setIncidentId(inc.getId());
      if (!String.valueOf(inc.getProcessInstanceKey()).equals(processInstanceId)) {
        final String callActivityInstanceId = TreePath
            .extractFlowNodeInstanceId(inc.getTreePath(), currentTreePath);
        incData.setFinalFlowNodeInstanceId(callActivityInstanceId);
        flowNodeInstanceIdsSet.add(callActivityInstanceId);
      } else {
        incData.setFinalFlowNodeInstanceId(String.valueOf(inc.getFlowNodeInstanceKey()));
        incData.setFinalFlowNodeId(inc.getFlowNodeId());
      }
      incDatas.put(inc.getId(), incData);
    }

    if (flowNodeInstanceIdsSet.size() > 0) {
      //select flowNodeIds by flowNodeInstanceIds
      final Map<String, String> flowNodeIdsMap = getFlowNodeIds(flowNodeInstanceIdsSet);

      //set flow node id, where not yet set
      incDatas.values().stream()
          .filter(iData -> iData.getFinalFlowNodeId() == null)
          .forEach(iData -> iData
              .setFinalFlowNodeId(flowNodeIdsMap.get(iData.getFinalFlowNodeInstanceId())));
    }
    return incDatas;
  }

  private Map<String, String> getFlowNodeIds(final Set<String> flowNodeInstanceIds) {
    final Map<String, String> flowNodeIdsMap = new HashMap<>();
    final QueryBuilder q = termsQuery(FlowNodeInstanceTemplate.ID, flowNodeInstanceIds);
    final SearchRequest request = ElasticsearchUtil
        .createSearchRequest(flowNodeInstanceTemplate, ONLY_RUNTIME)
        .source(new SearchSourceBuilder().query(q)
            .fetchSource(
                new String[]{FlowNodeInstanceTemplate.ID, FlowNodeInstanceTemplate.FLOW_NODE_ID},
                null));
    try {
      scrollWith(request, esClient, searchHits -> {
        Arrays.stream(searchHits.getHits()).forEach(h -> flowNodeIdsMap.put(h.getId(),
            (String) h.getSourceAsMap().get(FlowNodeInstanceTemplate.FLOW_NODE_ID)));
      }, null, null);
    } catch (IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred when searching for flow node ids: " + e.getMessage(), e);
    }
    return flowNodeIdsMap;
  }

  public class IncidentDataHolder {
    private String incidentId;
    private String finalFlowNodeInstanceId;
    private String finalFlowNodeId;

    public String getIncidentId() {
      return incidentId;
    }

    public IncidentDataHolder setIncidentId(final String incidentId) {
      this.incidentId = incidentId;
      return this;
    }

    public String getFinalFlowNodeInstanceId() {
      return finalFlowNodeInstanceId;
    }

    public IncidentDataHolder setFinalFlowNodeInstanceId(final String finalFlowNodeInstanceId) {
      this.finalFlowNodeInstanceId = finalFlowNodeInstanceId;
      return this;
    }

    public String getFinalFlowNodeId() {
      return finalFlowNodeId;
    }

    public IncidentDataHolder setFinalFlowNodeId(final String finalFlowNodeId) {
      this.finalFlowNodeId = finalFlowNodeId;
      return this;
    }
  }

}
