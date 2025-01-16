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
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.DecisionInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
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
    extends OpensearchSearchableDao<DecisionInstance, DecisionInstance>
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

    final List<DecisionInstance> decisionInstances;
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
  protected SearchRequest.Builder buildSearchRequest(final Query<DecisionInstance> query) {
    return super.buildSearchRequest(query)
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
  protected Class<DecisionInstance> getInternalDocumentModelClass() {
    return DecisionInstance.class;
  }

  @Override
  protected String getIndexName() {
    return decisionInstanceTemplate.getAlias();
  }

  @Override
  protected void buildFiltering(
      final Query<DecisionInstance> query, final SearchRequest.Builder request) {
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
                  queryDSLWrapper.term(
                      DecisionInstance.EVALUATION_FAILURE, filter.getEvaluationFailure()),
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
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected DecisionInstance convertInternalToApiResult(final DecisionInstance internalResult) {
    if (internalResult != null && StringUtils.isNotEmpty(internalResult.getEvaluationDate())) {
      internalResult.setEvaluationDate(
          dateTimeFormatter.convertGeneralToApiDateTime(internalResult.getEvaluationDate()));
    }

    return internalResult;
  }
}
