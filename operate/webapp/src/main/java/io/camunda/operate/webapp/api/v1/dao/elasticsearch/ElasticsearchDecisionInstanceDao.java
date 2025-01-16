/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.*;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
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
      decisionInstances =
          searchFor(new SearchSourceBuilder().query(termQuery(DecisionInstanceTemplate.ID, id)));
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

  private List<DecisionInstance> mapSearchHits(final SearchHit[] searchHitArray) {
    final List<DecisionInstance> decisionInstances =
        ElasticsearchUtil.mapSearchHits(searchHitArray, objectMapper, DecisionInstance.class);

    if (decisionInstances != null) {
      for (final DecisionInstance di : decisionInstances) {
        di.setEvaluationDate(dateTimeFormatter.convertGeneralToApiDateTime(di.getEvaluationDate()));
      }
    }

    return decisionInstances;
  }

  @Override
  public Results<DecisionInstance> search(final Query<DecisionInstance> query) throws APIException {

    final SearchSourceBuilder searchSourceBuilder =
        buildQueryOn(query, DecisionInstance.ID, new SearchSourceBuilder())
            .fetchSource(null, new String[] {EVALUATED_INPUTS, EVALUATED_OUTPUTS});

    try {
      final SearchRequest searchRequest =
          new SearchRequest()
              .indices(decisionInstanceTemplate.getAlias())
              .source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        final List<DecisionInstance> decisionInstances = mapSearchHits(searchHitArray);
        return new Results<DecisionInstance>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(decisionInstances)
            .setSortValues(sortValues);
      } else {
        return new Results<DecisionInstance>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (final Exception e) {
      throw new ServerException("Error in reading decision instance", e);
    }
  }

  @Override
  protected void buildFiltering(
      final Query<DecisionInstance> query, final SearchSourceBuilder searchSourceBuilder) {
    final DecisionInstance filter = query.getFilter();
    if (filter != null) {
      final List<QueryBuilder> queryBuilders = new ArrayList<>();
      queryBuilders.add(buildTermQuery(DecisionInstance.ID, filter.getId()));
      queryBuilders.add(buildTermQuery(DecisionInstance.KEY, filter.getKey()));
      queryBuilders.add(
          buildTermQuery(
              DecisionInstance.STATE, filter.getState() == null ? null : filter.getState().name()));
      queryBuilders.add(
          buildMatchDateQuery(DecisionInstance.EVALUATION_DATE, filter.getEvaluationDate()));
      queryBuilders.add(
          buildTermQuery(DecisionInstance.EVALUATION_FAILURE, filter.getEvaluationFailure()));
      queryBuilders.add(
          buildTermQuery(
              DecisionInstance.PROCESS_DEFINITION_KEY, filter.getProcessDefinitionKey()));
      queryBuilders.add(
          buildTermQuery(DecisionInstance.PROCESS_INSTANCE_KEY, filter.getProcessInstanceKey()));
      queryBuilders.add(buildTermQuery(DecisionInstance.DECISION_ID, filter.getDecisionId()));
      queryBuilders.add(buildTermQuery(DecisionInstance.TENANT_ID, filter.getTenantId()));
      queryBuilders.add(
          buildTermQuery(
              DecisionInstance.DECISION_DEFINITION_ID, filter.getDecisionDefinitionId()));
      queryBuilders.add(buildTermQuery(DecisionInstance.DECISION_NAME, filter.getDecisionName()));
      queryBuilders.add(
          buildTermQuery(DecisionInstance.DECISION_VERSION, filter.getDecisionVersion()));
      queryBuilders.add(
          buildTermQuery(
              DecisionInstance.DECISION_TYPE,
              filter.getDecisionType() == null ? null : filter.getDecisionType().name()));

      searchSourceBuilder.query(
          ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
    }
  }

  protected List<DecisionInstance> searchFor(final SearchSourceBuilder searchSource)
      throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(decisionInstanceTemplate.getAlias()).source(searchSource);
    final List<DecisionInstance> decisionInstances =
        tenantAwareClient.search(
            searchRequest,
            () -> {
              return ElasticsearchUtil.scroll(
                  searchRequest, DecisionInstance.class, objectMapper, elasticsearch);
            });

    if (decisionInstances != null) {
      for (final DecisionInstance di : decisionInstances) {
        di.setEvaluationDate(dateTimeFormatter.convertGeneralToApiDateTime(di.getEvaluationDate()));
      }
    }

    return decisionInstances;
  }
}
