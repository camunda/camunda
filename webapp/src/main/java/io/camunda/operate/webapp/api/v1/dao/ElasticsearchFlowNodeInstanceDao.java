/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("ElasticsearchFlowNodeInstanceDaoV1")
public class ElasticsearchFlowNodeInstanceDao extends ElasticsearchDao<FlowNodeInstance> implements FlowNodeInstanceDao {

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @Override
  protected void buildFiltering(
      final Query<FlowNodeInstance> query,
      final SearchSourceBuilder searchSourceBuilder) {
    final FlowNodeInstance filter = query.getFilter();
    final List<QueryBuilder> queryBuilders = new ArrayList<>();
    if (filter != null) {
      queryBuilders.add(buildTermQuery(FlowNodeInstance.KEY, filter.getKey()));
      queryBuilders.add(buildTermQuery(FlowNodeInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()));
      queryBuilders.add(buildTermQuery(FlowNodeInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()));
      queryBuilders.add(buildMatchDateQuery(FlowNodeInstance.START_DATE, filter.getStartDate()));
      queryBuilders.add(buildMatchDateQuery(FlowNodeInstance.END_DATE, filter.getEndDate()));
      queryBuilders.add(buildTermQuery(FlowNodeInstance.STATE, filter.getState()));
      queryBuilders.add(buildTermQuery(FlowNodeInstance.TYPE, filter.getType()));
      queryBuilders.add(buildTermQuery(FlowNodeInstance.FLOW_NODE_ID, filter.getFlowNodeId()));
      queryBuilders.add(buildTermQuery(FlowNodeInstance.INCIDENT, filter.getIncident()));
      queryBuilders.add(buildTermQuery(FlowNodeInstance.INCIDENT_KEY, filter.getIncidentKey()));
    }
    searchSourceBuilder.query(joinWithAnd(queryBuilders.toArray(new QueryBuilder[]{})));
  }

  @Override
  public FlowNodeInstance byKey(final Long key) throws APIException {
    logger.debug("byKey {}", key);
    List<FlowNodeInstance> flowNodeInstances;
    try {
      flowNodeInstances = searchFor(new SearchSourceBuilder().query(termQuery(FlowNodeInstance.KEY, key)));
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading flownode instance for key %s", key), e);
    }
    if (flowNodeInstances.isEmpty()) {
      throw new ResourceNotFoundException(String.format("No flownode instance found for key %s ", key));
    }
    if (flowNodeInstances.size() > 1) {
      throw new ServerException(String.format("Found more than one flownode instances for key %s", key));
    }
    return flowNodeInstances.get(0);
  }

  @Override
  public Results<FlowNodeInstance> search(final Query<FlowNodeInstance> query) throws APIException {
    logger.debug("search {}", query);
    final SearchSourceBuilder searchSourceBuilder = buildQueryOn(
        query,
        FlowNodeInstance.KEY,
        new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest = new SearchRequest().indices(
              flowNodeInstanceIndex.getAlias())
          .source(searchSourceBuilder);
      final SearchResponse searchResponse = elasticsearch.search(searchRequest,
          RequestOptions.DEFAULT);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        List<FlowNodeInstance> flowNodeInstances =
            ElasticsearchUtil.mapSearchHits(searchHitArray, this::searchHitToFlowNodeInstance);
        return new Results<FlowNodeInstance>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(flowNodeInstances)
            .setSortValues(sortValues);
      } else {
        return new Results<FlowNodeInstance>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading flownode instances", e);
    }
  }

  private FlowNodeInstance searchHitToFlowNodeInstance(final SearchHit searchHit) {
    final Map<String,Object> searchHitAsMap = searchHit.getSourceAsMap();
    return new FlowNodeInstance()
        .setKey((Long) searchHitAsMap.get(FlowNodeInstance.KEY))
        .setProcessInstanceKey((Long) searchHitAsMap.get(FlowNodeInstance.PROCESS_INSTANCE_KEY))
        .setProcessDefinitionKey((Long) searchHitAsMap.get(FlowNodeInstance.PROCESS_DEFINITION_KEY))
        .setStartDate((String) searchHitAsMap.get(FlowNodeInstance.START_DATE))
        .setEndDate((String) searchHitAsMap.get(FlowNodeInstance.END_DATE))
        .setType((String) searchHitAsMap.get(FlowNodeInstance.TYPE))
        .setState((String) searchHitAsMap.get(FlowNodeInstance.STATE))
        .setFlowNodeId((String) searchHitAsMap.get(FlowNodeInstance.FLOW_NODE_ID))
        .setIncident((Boolean) searchHitAsMap.get(FlowNodeInstance.INCIDENT))
        .setIncidentKey((Long) searchHitAsMap.get(FlowNodeInstance.INCIDENT_KEY));
  }

  protected List<FlowNodeInstance> searchFor(final SearchSourceBuilder searchSourceBuilder){
    try {
      final SearchRequest searchRequest = new SearchRequest(flowNodeInstanceIndex.getAlias())
          .source(searchSourceBuilder);
      final SearchResponse searchResponse = elasticsearch.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        return ElasticsearchUtil.mapSearchHits(searchHitArray, this::searchHitToFlowNodeInstance);
      } else {
        return List.of();
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading incidents", e);
    }
  }
}
