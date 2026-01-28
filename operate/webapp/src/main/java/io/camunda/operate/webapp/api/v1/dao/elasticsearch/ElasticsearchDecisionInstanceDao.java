/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.*;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.DecisionInstanceDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchDecisionInstanceDaoV1")
public class ElasticsearchDecisionInstanceDao extends ElasticsearchDao<DecisionInstance>
    implements DecisionInstanceDao {

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Override
  public DecisionInstance byId(final String id) throws APIException {
    final List<DecisionInstance> decisionInstances;
    try {
      decisionInstances = searchFor(ElasticsearchUtil.termsQuery(ID, id));
      decisionInstances.forEach(
          di ->
              di.setEvaluationDate(
                  dateTimeFormatter.convertGeneralToApiDateTime(di.getEvaluationDate())));
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

    return decisionInstances.get(0);
  }

  @Override
  public Results<DecisionInstance> search(final Query<DecisionInstance> query) throws APIException {
    final var searchRequestBuilder =
        buildQueryOn(query, DecisionInstance.ID, new SearchRequest.Builder(), true);

    try {
      final var searchReq =
          searchRequestBuilder
              .index(decisionInstanceTemplate.getAlias())
              .source(s -> s.filter(f -> f.excludes(EVALUATED_INPUTS, EVALUATED_OUTPUTS)))
              .build();
      final var res = searchWithResultsReturn(searchReq, DecisionInstance.class);
      resultPostProcessing(res);
      return res;
    } catch (final Exception e) {
      throw new ServerException("Error in reading decision instance", e);
    }
  }

  @Override
  protected void buildFiltering(
      final Query<DecisionInstance> query,
      final Builder searchRequestBuilder,
      final boolean isTenantAware) {
    final DecisionInstance filter = query.getFilter();

    if (filter == null) {
      final var finalQuery =
          isTenantAware
              ? tenantHelper.makeQueryTenantAware(ElasticsearchUtil.matchAllQuery())
              : ElasticsearchUtil.matchAllQuery();
      searchRequestBuilder.query(finalQuery);
      return;
    }

    final var idQ =
        buildIfPresent(DecisionInstance.ID, filter.getId(), ElasticsearchUtil::termsQuery);

    final var keyQ =
        buildIfPresent(DecisionInstance.KEY, filter.getKey(), ElasticsearchUtil::termsQuery);

    final var stateQ =
        buildIfPresent(
            DecisionInstance.STATE,
            filter.getState() == null ? null : filter.getState().name(),
            ElasticsearchUtil::termsQuery);

    final var evalDateQ =
        buildIfPresent(
            DecisionInstance.EVALUATION_DATE,
            filter.getEvaluationDate(),
            this::buildMatchDateQuery);

    final var evalFailureQ =
        filter.getEvaluationFailure() == null
            ? null
            : ElasticsearchUtil.joinWithOr(
                ElasticsearchUtil.termsQuery(
                    DecisionInstance.EVALUATION_FAILURE_MESSAGE, filter.getEvaluationFailure()),
                ElasticsearchUtil.termsQuery(
                    DecisionInstance.EVALUATION_FAILURE, filter.getEvaluationFailure()));

    final var procDefKeyQ =
        buildIfPresent(
            DecisionInstance.PROCESS_DEFINITION_KEY,
            filter.getProcessDefinitionKey(),
            ElasticsearchUtil::termsQuery);

    final var procInstKeyQ =
        buildIfPresent(
            DecisionInstance.PROCESS_INSTANCE_KEY,
            filter.getProcessInstanceKey(),
            ElasticsearchUtil::termsQuery);

    final var decisionIdQ =
        buildIfPresent(
            DecisionInstance.DECISION_ID, filter.getDecisionId(), ElasticsearchUtil::termsQuery);

    final var tenantIdQ =
        buildIfPresent(
            DecisionInstance.TENANT_ID, filter.getTenantId(), ElasticsearchUtil::termsQuery);

    final var decisionDefIdQ =
        buildIfPresent(
            DecisionInstance.DECISION_DEFINITION_ID,
            filter.getDecisionDefinitionId(),
            ElasticsearchUtil::termsQuery);

    final var decisionNameQ =
        buildIfPresent(
            DecisionInstance.DECISION_NAME,
            filter.getDecisionName(),
            ElasticsearchUtil::termsQuery);

    final var decisionVersionQ =
        buildIfPresent(
            DecisionInstance.DECISION_VERSION,
            filter.getDecisionVersion(),
            ElasticsearchUtil::termsQuery);

    final var decisionTypeQ =
        buildIfPresent(
            DecisionInstance.DECISION_TYPE,
            filter.getDecisionType() == null ? null : filter.getDecisionType().name(),
            ElasticsearchUtil::termsQuery);

    final var andOfAllQueries =
        ElasticsearchUtil.joinWithAnd(
            idQ,
            keyQ,
            stateQ,
            evalDateQ,
            evalFailureQ,
            procDefKeyQ,
            procInstKeyQ,
            decisionIdQ,
            tenantIdQ,
            decisionDefIdQ,
            decisionNameQ,
            decisionVersionQ,
            decisionTypeQ);

    final var finalQuery =
        isTenantAware ? tenantHelper.makeQueryTenantAware(andOfAllQueries) : andOfAllQueries;

    searchRequestBuilder.query(finalQuery);
  }

  private void resultPostProcessing(final Results<DecisionInstance> results) {
    for (final var item : results.getItems()) {
      final var msg = item.getEvaluationFailureMessage();

      if (msg != null) {
        item.setEvaluationFailure(msg);
      }

      item.setEvaluationDate(
          dateTimeFormatter.convertGeneralToApiDateTime(item.getEvaluationDate()));
    }
  }

  protected List<DecisionInstance> searchFor(
      final co.elastic.clients.elasticsearch._types.query_dsl.Query query) {
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(decisionInstanceTemplate.getAlias())
            .query(tenantAwareQuery);

    final var decisionInstances =
        ElasticsearchUtil.scrollAllStream(esClient, searchRequestBuilder, DecisionInstance.class)
            .flatMap(res -> res.hits().hits().stream())
            .map(Hit::source)
            .toList();

    return decisionInstances;
  }
}
