/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
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

@Component("ElasticsearchDecisionDefinitionDaoV1")
public class ElasticsearchDecisionDefinitionDao extends ElasticsearchDao<DecisionDefinition>
    implements DecisionDefinitionDao {

  @Autowired
  private DecisionIndex decisionIndex;

  @Autowired
  private DecisionRequirementsDao decisionRequirementsDao;

  @Override
  public DecisionDefinition byKey(Long key) throws APIException {
    List<DecisionDefinition> decisionDefinitions;
    try {
      decisionDefinitions = searchFor(new SearchSourceBuilder().query(termQuery(DecisionIndex.KEY, key)));
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading decision definition for key %s", key), e);
    }
    if (decisionDefinitions.isEmpty()) {
      throw new ResourceNotFoundException(String.format("No decision definition found for key %s ", key));
    }
    if (decisionDefinitions.size() > 1) {
      throw new ServerException(String.format("Found more than one decision definition for key %s", key));
    }

    DecisionDefinition decisionDefinition = decisionDefinitions.get(0);
    DecisionRequirements decisionRequirements = decisionRequirementsDao.byKey(decisionDefinition.getDecisionRequirementsKey());
    decisionDefinition.setDecisionRequirementsName(decisionRequirements.getName());
    decisionDefinition.setDecisionRequirementsVersion(decisionRequirements.getVersion());

    return decisionDefinition;
  }

  @Override
  protected void buildFiltering(Query<DecisionDefinition> query, SearchSourceBuilder searchSourceBuilder) {

  }

  protected List<DecisionDefinition> searchFor(final SearchSourceBuilder searchSource) throws IOException {
    final SearchRequest searchRequest = new SearchRequest(decisionIndex.getAlias()).source(searchSource);
    return ElasticsearchUtil.scroll(searchRequest, DecisionDefinition.class, objectMapper, elasticsearch);
  }
}
