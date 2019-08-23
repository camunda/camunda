/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.io.IOException;
import java.util.List;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.es.schema.templates.EventTemplate;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.webapp.rest.dto.EventQueryDto;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class EventReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(EventReader.class);

  @Autowired
  private EventTemplate eventTemplate;

  public List<EventEntity> queryEvents(EventQueryDto eventQuery) {
    SearchRequest searchRequest = createSearchRequest(eventQuery);

    applySorting(searchRequest);

    try {
      return scroll(searchRequest);
    } catch (IOException e) {
      final String message = String.format("Exception occurred, while obtaining events: %s", e.getMessage());
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }

  private void applySorting(SearchRequest searchRequest) {
    searchRequest.source()
      .sort(EventTemplate.DATE_TIME, SortOrder.ASC)
      .sort(EventTemplate.ID, SortOrder.ASC);
  }

  protected List<EventEntity> scroll(SearchRequest request) throws IOException {
    return ElasticsearchUtil.scroll(request, EventEntity.class, objectMapper, esClient);
  }

  private SearchRequest createSearchRequest(EventQueryDto eventQuery) {
    TermQueryBuilder workflowInstanceQuery = null;
    if (eventQuery.getWorkflowInstanceId() != null) {
      workflowInstanceQuery = termQuery(EventTemplate.WORKFLOW_INSTANCE_KEY, eventQuery.getWorkflowInstanceId());
    }
    TermQueryBuilder activityInstanceQ = null;
    if (eventQuery.getActivityInstanceId() != null) {
      activityInstanceQ = termQuery(EventTemplate.FLOW_NODE_INSTANCE_KEY, eventQuery.getActivityInstanceId());
    }
    QueryBuilder query = ElasticsearchUtil.joinWithAnd(workflowInstanceQuery, activityInstanceQ);
    if (query == null) {
      query = matchAllQuery();
    }

    ConstantScoreQueryBuilder constantScoreQuery = QueryBuilders.constantScoreQuery(query);
    logger.debug("Events search request: \n{}", constantScoreQuery.toString());

    return new SearchRequest(eventTemplate.getAlias())
      .source(new SearchSourceBuilder()
        .query(constantScoreQuery));
  }

}
