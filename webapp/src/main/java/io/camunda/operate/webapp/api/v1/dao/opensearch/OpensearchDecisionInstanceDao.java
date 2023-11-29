/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.property.OpensearchProperties;
import io.camunda.operate.property.OperateProperties;
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

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionInstanceDao extends OpensearchSearchableDao<DecisionInstance, DecisionInstance> implements DecisionInstanceDao {

  private final DecisionInstanceTemplate decisionInstanceTemplate;

  private final OpensearchProperties opensearchProperties;

  public OpensearchDecisionInstanceDao(OpensearchQueryDSLWrapper queryDSLWrapper, OpensearchRequestDSLWrapper requestDSLWrapper,
                                       RichOpenSearchClient richOpenSearchClient, DecisionInstanceTemplate decisionInstanceTemplate,
                                       OperateProperties operateProperties){
    super(queryDSLWrapper, requestDSLWrapper,richOpenSearchClient);
    this.decisionInstanceTemplate = decisionInstanceTemplate;
    this.opensearchProperties = operateProperties.getOpensearch();
  }

  @Override
  protected DecisionInstance convertInternalToApiResult(DecisionInstance internalResult) {
    return internalResult;
  }

  @Override
  public DecisionInstance byId(String id) throws APIException {
    if (id == null) {
      throw new ServerException("ID provided cannot be null");
    }

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
      var queryTerms = Stream.of(
        queryDSLWrapper.term(DecisionInstance.ID, filter.getId()),
        queryDSLWrapper.term(DecisionInstance.KEY, filter.getKey()),
        queryDSLWrapper.term(DecisionInstance.STATE, filter.getState() == null ? null : filter.getState().name()),
        queryDSLWrapper.matchDateQuery(DecisionInstance.EVALUATION_DATE, filter.getEvaluationDate(), opensearchProperties.getDateFormat()),
        queryDSLWrapper.term(DecisionInstance.EVALUATION_FAILURE, filter.getEvaluationFailure()),
        queryDSLWrapper.term(DecisionInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()),
        queryDSLWrapper.term(DecisionInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
        queryDSLWrapper.term(DecisionInstance.DECISION_ID, filter.getDecisionId()),
        queryDSLWrapper.term(DecisionInstance.TENANT_ID, filter.getTenantId()),
        queryDSLWrapper.term(DecisionInstance.DECISION_DEFINITION_ID, filter.getDecisionDefinitionId()),
        queryDSLWrapper.term(DecisionInstance.DECISION_NAME, filter.getDecisionName()),
        queryDSLWrapper.term(DecisionInstance.DECISION_VERSION, filter.getDecisionVersion()),
        queryDSLWrapper.term(DecisionInstance.DECISION_TYPE, filter.getDecisionType() == null ? null : filter.getDecisionType().name())
      ).filter(Objects::nonNull).collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }
}
