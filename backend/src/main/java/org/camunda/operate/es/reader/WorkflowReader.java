/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.rest.exception.NotFoundException;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.camunda.operate.es.schema.indices.WorkflowIndex.BPMN_XML;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;

@Component
public class WorkflowReader {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowReader.class);

  @Autowired
  private TransportClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private WorkflowIndex workflowType;

  /**
   * Gets the workflow diagram XML as a string.
   * @param workflowId
   * @return
   */
  public String getDiagram(String workflowId) {
    final IdsQueryBuilder q = idsQuery().addIds(workflowId);

    final SearchResponse response = esClient.prepareSearch(workflowType.getAlias())
      .setFetchSource(BPMN_XML, null)
      .setQuery(q)
      .get();

    if (response.getHits().totalHits == 1) {
      Map<String, Object> result = response.getHits().getHits()[0].getSourceAsMap();
      return (String) result.get(BPMN_XML);
    } else if (response.getHits().totalHits > 1) {
      throw new NotFoundException(String.format("Could not find unique workflow with id '%s'.", workflowId));
    } else {
      throw new NotFoundException(String.format("Could not find workflow with id '%s'.", workflowId));
    }
  }

  /**
   * Gets the workflow by id.
   * @param workflowId
   * @return
   */
  public WorkflowEntity getWorkflow(String workflowId) {
    final IdsQueryBuilder q = idsQuery().addIds(workflowId);

    final SearchResponse response = esClient.prepareSearch(workflowType.getAlias())
      .setQuery(q)
      .get();

    if (response.getHits().totalHits == 1) {
      return fromSearchHit(response.getHits().getHits()[0].getSourceAsString());
    } else if (response.getHits().totalHits > 1) {
      throw new NotFoundException(String.format("Could not find unique workflow with id '%s'.", workflowId));
    } else {
      throw new NotFoundException(String.format("Could not find workflow with id '%s'.", workflowId));
    }
  }

  private WorkflowEntity fromSearchHit(String workflowString) {
    return ElasticsearchUtil.fromSearchHit(workflowString, objectMapper, WorkflowEntity.class);
  }

  /**
   * Returns map of Workflow entities grouped by bpmnProcessId.
   * @return
   */
  public Map<String, List<WorkflowEntity>> getWorkflowsGrouped() {
    final String groupsAggName = "group_by_bpmnProcessId";
    final String workflowsAggName = "workflows";
    final SearchRequestBuilder searchRequestBuilder =
      esClient.prepareSearch(workflowType.getAlias()).setSize(0)
        .addAggregation(
          terms(groupsAggName)
            .field(WorkflowIndex.BPMN_PROCESS_ID)
            .size(ElasticsearchUtil.TERMS_AGG_SIZE)
            .subAggregation(
              topHits(workflowsAggName)
                .fetchSource(new String[] { WorkflowIndex.ID, WorkflowIndex.NAME, WorkflowIndex.VERSION, WorkflowIndex.BPMN_PROCESS_ID  }, null)
                .size(ElasticsearchUtil.TOPHITS_AGG_SIZE)
                .sort(WorkflowIndex.VERSION, SortOrder.DESC)));

    logger.debug("Grouped workflow request: \n{}", searchRequestBuilder.toString());

    final SearchResponse searchResponse = searchRequestBuilder.get();

    final Terms groups = searchResponse.getAggregations().get(groupsAggName);

    Map<String, List<WorkflowEntity>> result = new HashMap<>();

    groups.getBuckets().stream().forEach(b -> {
      final String bpmnProcessId = b.getKeyAsString();
      result.put(bpmnProcessId, new ArrayList<>());

      final TopHits workflows = b.getAggregations().get(workflowsAggName);
      final SearchHit[] hits = workflows.getHits().getHits();
      for (SearchHit searchHit: hits) {
        final WorkflowEntity workflowEntity = fromSearchHit(searchHit.getSourceAsString());
        result.get(bpmnProcessId).add(workflowEntity);
      }
    });

    return result;
  }

  /**
   * Returns map of Workflow entities by workflow ids.
   * @return
   */
  public Map<String, WorkflowEntity> getWorkflows() {

    Map<String, WorkflowEntity> map = new HashMap<>();

    final SearchRequestBuilder searchRequestBuilder =
      esClient.prepareSearch(workflowType.getAlias());

    final List<WorkflowEntity> workflowsList = scroll(searchRequestBuilder);
    for (WorkflowEntity workflowEntity: workflowsList) {
      map.put(workflowEntity.getId(), workflowEntity);
    }
    return map;
  }

  private List<WorkflowEntity> scroll(SearchRequestBuilder builder) {
    return ElasticsearchUtil.scroll(builder, WorkflowEntity.class, objectMapper, esClient);
  }

}
