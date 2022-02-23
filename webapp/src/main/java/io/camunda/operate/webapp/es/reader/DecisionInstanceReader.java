/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.es.reader;

import static io.camunda.operate.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.entities.dmn.DecisionInstanceEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DecisionInstanceReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(DecisionInstanceReader.class);

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  public DecisionInstanceDto getDecisionInstance(String decisionInstanceId) {
    final QueryBuilder query = joinWithAnd(
        idsQuery().addIds(String.valueOf(decisionInstanceId)),
        termQuery(DecisionInstanceTemplate.ID, decisionInstanceId)
    );

    SearchRequest request = ElasticsearchUtil.createSearchRequest(decisionInstanceTemplate, ALL)
      .source(new SearchSourceBuilder()
      .query(constantScoreQuery(query)));

    try {
      final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        final DecisionInstanceEntity decisionInstance = ElasticsearchUtil
            .fromSearchHit(response.getHits().getHits()[0].getSourceAsString(), objectMapper,
                DecisionInstanceEntity.class);
        return DecisionInstanceDto.createFrom(decisionInstance);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(String
            .format("Could not find unique decision instance with id '%s'.", decisionInstanceId));
      } else {
        throw new NotFoundException(
            String.format("Could not find decision instance with id '%s'.", decisionInstanceId));
      }
    } catch (IOException ex) {
      throw new OperateRuntimeException(ex.getMessage(), ex);
    }
  }

}
