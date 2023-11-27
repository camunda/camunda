/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.DecisionInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionInstanceDao extends OpensearchKeyFilteringDao<DecisionInstance, DecisionInstance> implements DecisionInstanceDao {

  private final DecisionInstanceTemplate decisionInstanceTemplate;

  public OpensearchDecisionInstanceDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                                       DecisionInstanceTemplate decisionInstanceTemplate,
                                       RichOpenSearchClient richOpenSearchClient){
    super(queryDSLWrapper, requestDSLWrapper,richOpenSearchClient);
    this.decisionInstanceTemplate = decisionInstanceTemplate;
  }

  @Override
  protected DecisionInstance convertInternalToApiResult(DecisionInstance internalResult) {
    return internalResult;
  }

  @Override
  public DecisionInstance byId(String id) throws APIException {
    List<DecisionInstance> decisionInstances;
    try {
      var request = requestDSLWrapper.searchRequestBuilder(getIndexName())
          .query(queryDSLWrapper.withTenantCheck(queryDSLWrapper.term(DecisionInstanceTemplate.ID, id)));
      decisionInstances = richOpenSearchClient.doc().searchValues(request, getInternalDocumentModelClass());
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading decision instance for id %s", id), e);
    }
    if (decisionInstances.isEmpty()) {
      throw new ResourceNotFoundException(String.format("No decision instance found for id %s", id));
    }
    if (decisionInstances.size() > 1) {
      throw new ServerException(String.format("Found more than one decision instance for id %s", id));
    }
    return decisionInstances.get(0);
  }

  @Override
  protected SearchRequest.Builder buildSearchRequest(Query<DecisionInstance> query) {
    return super.buildSearchRequest(query).source(queryDSLWrapper.sourceExclude(DecisionInstanceTemplate.EVALUATED_INPUTS, DecisionInstanceTemplate.EVALUATED_OUTPUTS));
  }

  @Override
  protected String getKeyFieldName() {
    return DecisionInstanceTemplate.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(Long key) {
    return String.format("Error in reading decision instance for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(Long key) {
    return String.format("No decision instance found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(Long key) {
    return String.format("Found more than one decision instance for key %s", key);
  }

  @Override
  protected String getUniqueSortKey() {
    return DecisionInstance.ID;
  }

  @Override
  protected Class<DecisionInstance> getInternalDocumentModelClass() {
    return DecisionInstance.class;
  }

  @Override
  protected String getIndexName() {
    return decisionInstanceTemplate.getAlias();
  }

  @Override
  protected void buildFiltering(Query<DecisionInstance> query, SearchRequest.Builder request) {
    final DecisionInstance filter = query.getFilter();
    if (filter != null) {
      var queryTerms = Arrays.asList(
        queryDSLWrapper.buildTermQuery(DecisionInstance.ID, filter.getId()),
        queryDSLWrapper.buildTermQuery(DecisionInstance.KEY, filter.getKey()),
        queryDSLWrapper.buildTermQuery(DecisionInstance.STATE, filter.getState() == null ? null : filter.getState().name()),
        queryDSLWrapper.buildMatchDateQuery(DecisionInstance.EVALUATION_DATE, filter.getEvaluationDate()),
        queryDSLWrapper.buildTermQuery(DecisionInstance.EVALUATION_FAILURE, filter.getEvaluationFailure()),
        queryDSLWrapper.buildTermQuery(DecisionInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()),
        queryDSLWrapper.buildTermQuery(DecisionInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
        queryDSLWrapper.buildTermQuery(DecisionInstance.DECISION_ID, filter.getDecisionId()),
        queryDSLWrapper.buildTermQuery(DecisionInstance.TENANT_ID, filter.getTenantId()),
        queryDSLWrapper.buildTermQuery(DecisionInstance.DECISION_DEFINITION_ID, filter.getDecisionDefinitionId()),
        queryDSLWrapper.buildTermQuery(DecisionInstance.DECISION_NAME, filter.getDecisionName()),
        queryDSLWrapper.buildTermQuery(DecisionInstance.DECISION_VERSION, filter.getDecisionVersion()),
        queryDSLWrapper.buildTermQuery(DecisionInstance.DECISION_TYPE, filter.getDecisionType() == null ? null : filter.getDecisionType().name())
      );

      request.query(queryDSLWrapper.and(queryTerms));
    }
  }
}
