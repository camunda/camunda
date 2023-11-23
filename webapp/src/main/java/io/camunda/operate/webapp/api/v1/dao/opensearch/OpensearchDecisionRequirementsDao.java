/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.*;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionRequirementsDao extends OpensearchKeyFilteringDao<DecisionRequirements> implements DecisionRequirementsDao {

  private final DecisionRequirementsIndex decisionRequirementsIndex;

  public OpensearchDecisionRequirementsDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
       DecisionRequirementsIndex decisionRequirementsIndex,
      RichOpenSearchClient richOpenSearchClient){
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.decisionRequirementsIndex = decisionRequirementsIndex;
  }

  @Override
  protected String getKeyFieldName() {
    return DecisionRequirements.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(Long key) {
    return String.format("Error in reading decision requirements for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(Long key) {
    return String.format("No decision requirements found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(Long key) {
    return String.format("Found more than one decision requirements for key %s", key);
  }

  @Override
  public List<DecisionRequirements> byKeys(Set<Long> keys) throws APIException {
    final List<Long> nonNullKeys = (keys == null) ? List.of() : keys.stream().filter(Objects::nonNull).toList();
    if (nonNullKeys.isEmpty()) {
      return List.of();
    }
    try{
      var request = requestDSLWrapper.searchRequestBuilder(
          getIndexName()).query(
              queryDSLWrapper.longTerms(getKeyFieldName(), nonNullKeys));
      return richOpenSearchClient.doc().scrollValues(request, DecisionRequirements.class);
    } catch (Exception e) {
      throw new ServerException("Error in reading decision requirements by keys", e);
    }
  }

  @Override
  public String xmlByKey(Long key) throws APIException {
    validateKey(key);
    var request =requestDSLWrapper.searchRequestBuilder(getIndexName())
        .query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.term(DecisionRequirements.KEY, key)))
        .source(queryDSLWrapper.sourceInclude(DecisionRequirementsIndex.XML));
    try {
      var response = richOpenSearchClient.doc().search(request, Map.class);
      if (response.hits().total().value() == 1) {
        return response.hits().hits().get(0).source().get(DecisionRequirementsIndex.XML).toString();
      }
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading decision requirements as xml for key %s", key), e);
    }
    throw new ResourceNotFoundException(String.format("Decision requirements for key %s not found.", key));
  }

  @Override
  protected String getUniqueSortKey() {
    return DecisionRequirements.KEY;
  }

  @Override
  protected Class<DecisionRequirements> getModelClass() {
    return DecisionRequirements.class;
  }

  @Override
  protected String getIndexName() {
    return decisionRequirementsIndex.getAlias();
  }

  @Override
  protected void buildFiltering(Query<DecisionRequirements> query, SearchRequest.Builder request) {
    final DecisionRequirements filter = query.getFilter();
    if (filter != null) {
      var queryTerms = Arrays.asList(
          buildTermQuery(DecisionRequirements.ID, filter.getId()), buildTermQuery(DecisionRequirements.KEY, filter.getKey()),
          buildTermQuery(DecisionRequirements.DECISION_REQUIREMENTS_ID, filter.getDecisionRequirementsId()),
          buildTermQuery(DecisionRequirements.TENANT_ID, filter.getTenantId()),
          buildTermQuery(DecisionRequirements.NAME, filter.getName()),
          buildTermQuery(DecisionRequirements.VERSION, filter.getVersion()),
          buildTermQuery(DecisionRequirements.RESOURCE_NAME, filter.getResourceName())
      );

      request.query(queryDSLWrapper.and(queryTerms));
    }
  }
}
