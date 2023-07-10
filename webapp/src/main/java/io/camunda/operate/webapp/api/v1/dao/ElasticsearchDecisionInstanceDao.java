/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component("ElasticsearchDecisionInstanceDaoV1")
public class ElasticsearchDecisionInstanceDao extends ElasticsearchDao<DecisionInstance>
    implements DecisionInstanceDao {

  @Autowired
  private DecisionInstanceTemplate decisionInstanceTemplate;

  @Override
  public DecisionInstance byId(String id) throws APIException {
    List<DecisionInstance> decisionInstances;
    try {
      decisionInstances = searchFor(new SearchSourceBuilder().query(termQuery(DecisionInstanceTemplate.ID, id)));
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading decision instance for id %s", id), e);
    }
    if (decisionInstances.isEmpty()) {
      throw new ResourceNotFoundException(String.format("No decision instance found for id %s", id));
    }
    if (decisionInstances.size() > 1) {
      throw new ServerException(String.format("Found more than one decision instance for id %s", id));
    }

    DecisionInstance decisionInstance = decisionInstances.get(0);

    return decisionInstance;
  }

  @Override
  protected void buildFiltering(Query<DecisionInstance> query, SearchSourceBuilder searchSourceBuilder) {

  }

  protected List<DecisionInstance> searchFor(final SearchSourceBuilder searchSource) throws IOException {
    final SearchRequest searchRequest = new SearchRequest(decisionInstanceTemplate.getAlias()).source(searchSource);
    return ElasticsearchUtil.scroll(searchRequest, DecisionInstance.class, objectMapper, elasticsearch);
  }
}
