/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.DecisionDefinitionDao;
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionDefinitionDao extends OpensearchKeyFilteringDao<DecisionDefinition> implements DecisionDefinitionDao {

  private final DecisionIndex decisionIndex;

  private final DecisionRequirementsIndex decisionRequirementsIndex;

  private final DecisionRequirementsDao decisionRequirementsDao;

  public OpensearchDecisionDefinitionDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
      DecisionIndex decisionIndex, DecisionRequirementsIndex decisionRequirementsIndex, DecisionRequirementsDao decisionRequirementsDao,
      RichOpenSearchClient richOpenSearchClient) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.decisionIndex = decisionIndex;
    this.decisionRequirementsIndex = decisionRequirementsIndex;
    this.decisionRequirementsDao = decisionRequirementsDao;
  }

  @Override
  protected String getKeyFieldName() {
    return DecisionIndex.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(Long key) {
    return String.format("Error in reading decision definition for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(Long key) {
    return String.format("No decision definition found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(Long key) {
    return String.format("Found more than one decision definition for key %s", key);
  }

  @Override
  protected String getUniqueSortKey() {
    return DecisionDefinition.KEY;
  }

  @Override
  protected Class<DecisionDefinition> getModelClass() {
    return DecisionDefinition.class;
  }

  @Override
  protected String getIndexName() {
    return decisionIndex.getAlias();
  }

  @Override
  public DecisionDefinition byKey(Long key) {
    var decisionDefinition = super.byKey(key);
    DecisionRequirements decisionRequirements = decisionRequirementsDao.byKey(decisionDefinition.getDecisionRequirementsKey());
    decisionDefinition.setDecisionRequirementsName(decisionRequirements.getName());
    decisionDefinition.setDecisionRequirementsVersion(decisionRequirements.getVersion());
    return decisionDefinition;
  }

  @Override
  protected void buildFiltering(Query<DecisionDefinition> query, SearchRequest.Builder request) {
    DecisionDefinition filter = query.getFilter();

    if (filter != null) {
      List<org.opensearch.client.opensearch._types.query_dsl.Query> queryTerms = new LinkedList<>();
      queryTerms.add(buildTermQuery(DecisionDefinition.ID, filter.getId()));
      queryTerms.add(buildTermQuery(DecisionDefinition.KEY, filter.getKey()));
      queryTerms.add(buildTermQuery(DecisionDefinition.DECISION_ID, filter.getDecisionId()));
      queryTerms.add(buildTermQuery(DecisionDefinition.TENANT_ID, filter.getTenantId()));
      queryTerms.add(buildTermQuery(DecisionDefinition.NAME, filter.getName()));
      queryTerms.add(buildTermQuery(DecisionDefinition.VERSION, filter.getVersion()));
      queryTerms.add(buildTermQuery(DecisionDefinition.DECISION_REQUIREMENTS_ID, filter.getDecisionRequirementsId()));
      queryTerms.add(buildTermQuery(DecisionDefinition.DECISION_REQUIREMENTS_KEY, filter.getDecisionRequirementsKey()));
      queryTerms.add(buildFilteringBy(filter.getDecisionRequirementsName(), filter.getDecisionRequirementsVersion()));

      request.query(queryDSLWrapper.and(queryTerms));
    }
  }

  @Override
  public Results<DecisionDefinition> search(Query<DecisionDefinition> query) {
    var results = super.search(query);
    var decisionDefinitions = results.getItems();
    populateDecisionRequirementsNameAndVersion(decisionDefinitions);
    return results;
  }

  /**
   * populateDecisionRequirementsNameAndVersion - adds decisionRequirementsName and decisionRequirementsVersion fields to the decision
   * definitions
   */
  private void populateDecisionRequirementsNameAndVersion(List<DecisionDefinition> decisionDefinitions) {
    Set<Long> decisionRequirementsKeys = decisionDefinitions.stream().map(DecisionDefinition::getDecisionRequirementsKey)
        .collect(Collectors.toSet());
    List<DecisionRequirements> decisionRequirements = decisionRequirementsDao.byKeys(decisionRequirementsKeys);

    Map<Long, DecisionRequirements> decisionReqMap = new HashMap<>();
    decisionRequirements.forEach(decisionReq -> decisionReqMap.put(decisionReq.getKey(), decisionReq));
    decisionDefinitions.forEach(decisionDef -> {
      DecisionRequirements decisionReq = (decisionDef.getDecisionRequirementsKey() == null) ?
          null :
          decisionReqMap.get(decisionDef.getDecisionRequirementsKey());
      if (decisionReq != null) {
        decisionDef.setDecisionRequirementsName(decisionReq.getName());
        decisionDef.setDecisionRequirementsVersion(decisionReq.getVersion());
      }
    });
  }

  private org.opensearch.client.opensearch._types.query_dsl.Query buildFilteringBy(String decisionRequirementsName,
      Integer decisionRequirementsVersion) {
    try {
      List<org.opensearch.client.opensearch._types.query_dsl.Query> queryTerms = new LinkedList<>();
      queryTerms.add(buildTermQuery(DecisionRequirementsIndex.NAME, decisionRequirementsName));
      queryTerms.add(buildTermQuery(DecisionRequirementsIndex.VERSION, decisionRequirementsVersion));
      var query = queryDSLWrapper.and(queryTerms);
      if (query == null) {
        return null;
      }
      var request = requestDSLWrapper.searchRequestBuilder(decisionRequirementsIndex.getAlias())
          .query(queryDSLWrapper.withTenantCheck(query)).source(queryDSLWrapper.sourceInclude(DecisionRequirementsIndex.KEY));
      var decisionRequirements = richOpenSearchClient.doc().scrollValues(request, DecisionRequirements.class);
      final List<Long> nonNullKeys = decisionRequirements.stream().map(DecisionRequirements::getKey).filter(Objects::nonNull).toList();
      if (nonNullKeys.isEmpty()) {
        return queryDSLWrapper.matchNone();
      }
      return queryDSLWrapper.longTerms(DecisionDefinition.DECISION_REQUIREMENTS_KEY, nonNullKeys);
    } catch (Exception e) {
      throw new ServerException("Error in reading decision requirements by name and version", e);
    }
  }
}
