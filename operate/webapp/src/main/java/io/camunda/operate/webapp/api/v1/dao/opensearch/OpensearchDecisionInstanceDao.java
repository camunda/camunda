/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.DecisionInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.opensearch.OpensearchDecisionInstance;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionInstanceDao
    extends OpensearchSearchableDao<DecisionInstance, OpensearchDecisionInstance>
    implements DecisionInstanceDao {

  private final DecisionInstanceTemplate decisionInstanceTemplate;

  private final OperateDateTimeFormatter dateTimeFormatter;

  public OpensearchDecisionInstanceDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient,
      final DecisionInstanceTemplate decisionInstanceTemplate,
      final OperateDateTimeFormatter dateTimeFormatter) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.decisionInstanceTemplate = decisionInstanceTemplate;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  public DecisionInstance byId(final String id) throws APIException {
    if (id == null) {
      throw new ServerException("ID provided cannot be null");
    }

    final List<OpensearchDecisionInstance> decisionInstances;
    try {
      final var request =
          requestDSLWrapper
              .searchRequestBuilder(getIndexName())
              .query(
                  queryDSLWrapper.withTenantCheck(
                      queryDSLWrapper.term(DecisionInstanceTemplate.ID, id)));
      decisionInstances =
          richOpenSearchClient.doc().searchValues(request, getInternalDocumentModelClass());
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in reading decision instance for id %s", id), e);
    }
    if (decisionInstances.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No decision instance found for id %s", id));
    }
    if (decisionInstances.size() > 1) {
      throw new ServerException(
          String.format("Found more than one decision instance for id %s", id));
    }
    return convertInternalToApiResult(decisionInstances.get(0));
  }

  @Override
  protected SearchRequest.Builder buildSearchRequest(
      final Query<DecisionInstance> query,
      final org.opensearch.client.opensearch._types.query_dsl.Query filtering) {
    return super.buildSearchRequest(query, filtering)
        .source(
            queryDSLWrapper.sourceExclude(
                DecisionInstanceTemplate.EVALUATED_INPUTS,
                DecisionInstanceTemplate.EVALUATED_OUTPUTS));
  }

  @Override
  protected String getUniqueSortKey() {
    return DecisionInstance.ID;
  }

  @Override
  protected Class<OpensearchDecisionInstance> getInternalDocumentModelClass() {
    return OpensearchDecisionInstance.class;
  }

  @Override
  protected String getIndexName() {
    return decisionInstanceTemplate.getAlias();
  }

  @Override
  protected org.opensearch.client.opensearch._types.query_dsl.Query buildFiltering(
      final Query<DecisionInstance> query) {
    final DecisionInstance filter = query.getFilter();
    if (filter != null) {
      final var queryTerms =
          Stream.of(
                  queryDSLWrapper.term(DecisionInstance.ID, filter.getId()),
                  queryDSLWrapper.term(DecisionInstance.KEY, filter.getKey()),
                  queryDSLWrapper.term(
                      DecisionInstance.STATE,
                      filter.getState() == null ? null : filter.getState().name()),
                  queryDSLWrapper.matchDateQuery(
                      DecisionInstance.EVALUATION_DATE,
                      filter.getEvaluationDate(),
                      dateTimeFormatter.getApiDateTimeFormatString()),
                  queryDSLWrapper.or(
                      queryDSLWrapper.term(
                          DecisionInstance.EVALUATION_FAILURE_MESSAGE,
                          filter.getEvaluationFailure()),
                      queryDSLWrapper.term(
                          DecisionInstance.EVALUATION_FAILURE, filter.getEvaluationFailure())),
                  queryDSLWrapper.term(
                      DecisionInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()),
                  queryDSLWrapper.term(
                      DecisionInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()),
                  queryDSLWrapper.term(DecisionInstance.DECISION_ID, filter.getDecisionId()),
                  queryDSLWrapper.term(DecisionInstance.TENANT_ID, filter.getTenantId()),
                  queryDSLWrapper.term(
                      DecisionInstance.DECISION_DEFINITION_ID, filter.getDecisionDefinitionId()),
                  queryDSLWrapper.term(DecisionInstance.DECISION_NAME, filter.getDecisionName()),
                  queryDSLWrapper.term(
                      DecisionInstance.DECISION_VERSION, filter.getDecisionVersion()),
                  queryDSLWrapper.term(
                      DecisionInstance.DECISION_TYPE,
                      filter.getDecisionType() == null ? null : filter.getDecisionType().name()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        return queryDSLWrapper.and(queryTerms);
      }
    }
    return queryDSLWrapper.matchAll();
  }

  @Override
  protected DecisionInstance convertInternalToApiResult(
      final OpensearchDecisionInstance internalResult) {
    final DecisionInstance apiResult = new DecisionInstance();
    if (internalResult != null) {
      apiResult
          .setId(internalResult.id())
          .setKey(internalResult.key())
          .setState(internalResult.state())
          .setProcessDefinitionKey(internalResult.processDefinitionKey())
          .setProcessInstanceKey(internalResult.processInstanceKey())
          .setDecisionId(internalResult.decisionId())
          .setTenantId(internalResult.tenantId())
          .setDecisionDefinitionId(internalResult.decisionDefinitionId())
          .setDecisionName(internalResult.decisionName())
          .setDecisionVersion(internalResult.decisionVersion())
          .setDecisionType(internalResult.decisionType())
          .setResult(internalResult.result());

      if (StringUtils.isNotEmpty(internalResult.evaluationDate())) {
        apiResult.setEvaluationDate(
            dateTimeFormatter.convertGeneralToApiDateTime(internalResult.evaluationDate()));
      }
      final String evaluationFailure =
          internalResult.evaluationFailureMessage() != null
              ? internalResult.evaluationFailureMessage()
              : internalResult.evaluationFailure();
      apiResult.setEvaluationFailure(evaluationFailure);
    }
    return apiResult;
  }
}
