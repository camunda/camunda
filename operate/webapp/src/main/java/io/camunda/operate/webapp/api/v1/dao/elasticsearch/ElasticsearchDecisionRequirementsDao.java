/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import java.io.IOException;
import java.util.*;
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
@Component("ElasticsearchDecisionRequirementsDaoV1")
public class ElasticsearchDecisionRequirementsDao extends ElasticsearchDao<DecisionRequirements>
    implements DecisionRequirementsDao {

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Override
  public DecisionRequirements byKey(final Long key) throws APIException {
    final List<DecisionRequirements> decisionRequirements;
    try {
      decisionRequirements =
          searchFor(new SearchSourceBuilder().query(termQuery(DecisionRequirementsIndex.KEY, key)));
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in reading decision requirements for key %s", key), e);
    }
    if (decisionRequirements.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No decision requirements found for key %s", key));
    }
    if (decisionRequirements.size() > 1) {
      throw new ServerException(
          String.format("Found more than one decision requirements for key %s", key));
    }
    return decisionRequirements.get(0);
  }

  @Override
  public List<DecisionRequirements> byKeys(final Set<Long> keys) throws APIException {
    final List<Long> nonNullKeys =
        (keys == null) ? List.of() : keys.stream().filter(Objects::nonNull).toList();
    if (nonNullKeys.isEmpty()) {
      return List.of();
    }
    try {
      return searchFor(
          new SearchSourceBuilder().query(termsQuery(DecisionRequirementsIndex.KEY, nonNullKeys)));
    } catch (final Exception e) {
      throw new ServerException("Error in reading decision requirements by keys", e);
    }
  }

  @Override
  public String xmlByKey(final Long key) throws APIException {
    try {
      final SearchRequest searchRequest =
          new SearchRequest(decisionRequirementsIndex.getAlias())
              .source(
                  new SearchSourceBuilder()
                      .query(termQuery(DecisionRequirementsIndex.KEY, key))
                      .fetchSource(DecisionRequirementsIndex.XML, null));
      final SearchResponse response = tenantAwareClient.search(searchRequest);
      if (response.getHits().getTotalHits().value == 1) {
        final Map<String, Object> result = response.getHits().getHits()[0].getSourceAsMap();
        return (String) result.get(DecisionRequirementsIndex.XML);
      }
    } catch (final IOException e) {
      throw new ServerException(
          String.format("Error in reading decision requirements as xml for key %s", key), e);
    }
    throw new ResourceNotFoundException(
        String.format("Decision requirements for key %s not found.", key));
  }

  @Override
  public Results<DecisionRequirements> search(final Query<DecisionRequirements> query)
      throws APIException {

    final SearchSourceBuilder searchSourceBuilder =
        buildQueryOn(query, DecisionRequirements.KEY, new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest =
          new SearchRequest()
              .indices(decisionRequirementsIndex.getAlias())
              .source(searchSourceBuilder);
      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        final List<DecisionRequirements> decisionRequirements =
            ElasticsearchUtil.mapSearchHits(
                searchHitArray, objectMapper, DecisionRequirements.class);
        return new Results<DecisionRequirements>()
            .setTotal(searchHits.getTotalHits().value)
            .setItems(decisionRequirements)
            .setSortValues(sortValues);
      } else {
        return new Results<DecisionRequirements>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (final Exception e) {
      throw new ServerException("Error in reading decision requirements", e);
    }
  }

  protected List<DecisionRequirements> searchFor(final SearchSourceBuilder searchSource)
      throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(decisionRequirementsIndex.getAlias()).source(searchSource);
    return tenantAwareClient.search(
        searchRequest,
        () -> {
          return ElasticsearchUtil.scroll(
              searchRequest, DecisionRequirements.class, objectMapper, elasticsearch);
        });
  }

  @Override
  protected void buildFiltering(
      final Query<DecisionRequirements> query, final SearchSourceBuilder searchSourceBuilder) {
    final DecisionRequirements filter = query.getFilter();
    if (filter != null) {
      final List<QueryBuilder> queryBuilders = new ArrayList<>();
      queryBuilders.add(buildTermQuery(DecisionRequirements.ID, filter.getId()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.KEY, filter.getKey()));
      queryBuilders.add(
          buildTermQuery(
              DecisionRequirements.DECISION_REQUIREMENTS_ID, filter.getDecisionRequirementsId()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.TENANT_ID, filter.getTenantId()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.NAME, filter.getName()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.VERSION, filter.getVersion()));
      queryBuilders.add(
          buildTermQuery(DecisionRequirements.RESOURCE_NAME, filter.getResourceName()));

      searchSourceBuilder.query(
          ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
    }
  }
}
