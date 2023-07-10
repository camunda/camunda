/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.dao;

import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component("ElasticsearchDecisionRequirementsDaoV1")
public class ElasticsearchDecisionRequirementsDao extends ElasticsearchDao<DecisionRequirements>
    implements DecisionRequirementsDao {

  @Autowired
  private DecisionRequirementsIndex decisionRequirementsIndex;

  @Override
  public DecisionRequirements byKey(Long key) throws APIException {
    List<DecisionRequirements> decisionRequirements;
    try {
      decisionRequirements = searchFor(new SearchSourceBuilder().query(termQuery(DecisionRequirementsIndex.KEY, key)));
    } catch (Exception e) {
      throw new ServerException(String.format("Error in reading decision requirements for key %s", key), e);
    }
    if (decisionRequirements.isEmpty()) {
      throw new ResourceNotFoundException(String.format("No decision requirements found for key %s", key));
    }
    if (decisionRequirements.size() > 1) {
      throw new ServerException(String.format("Found more than one decision requirements for key %s", key));
    }
    return decisionRequirements.get(0);
  }

  @Override
  public List<DecisionRequirements> byKeys(Set<Long> keys) throws APIException {
    final List<Long> nonNullKeys = (keys == null) ? List.of() : keys.stream().filter(Objects::nonNull).toList();
    if (nonNullKeys.isEmpty()) {
      return List.of();
    }
    try {
      return searchFor(new SearchSourceBuilder().query(termsQuery(DecisionRequirementsIndex.KEY, nonNullKeys)));
    } catch (Exception e) {
      throw new ServerException("Error in reading decision requirements by keys", e);
    }
  }

  @Override
  public Results<DecisionRequirements> search(Query<DecisionRequirements> query) throws APIException {

    final SearchSourceBuilder searchSourceBuilder = buildQueryOn(query, DecisionRequirements.KEY, new SearchSourceBuilder());
    try {
      final SearchRequest searchRequest = new SearchRequest().indices(decisionRequirementsIndex.getAlias()).source(searchSourceBuilder);
      final SearchResponse searchResponse = elasticsearch.search(searchRequest, RequestOptions.DEFAULT);
      final SearchHits searchHits = searchResponse.getHits();
      final SearchHit[] searchHitArray = searchHits.getHits();
      if (searchHitArray != null && searchHitArray.length > 0) {
        final Object[] sortValues = searchHitArray[searchHitArray.length - 1].getSortValues();
        List<DecisionRequirements> decisionRequirements = ElasticsearchUtil.mapSearchHits(searchHitArray, objectMapper, DecisionRequirements.class);
        return new Results<DecisionRequirements>().setTotal(searchHits.getTotalHits().value).setItems(decisionRequirements).setSortValues(sortValues);
      } else {
        return new Results<DecisionRequirements>().setTotal(searchHits.getTotalHits().value);
      }
    } catch (Exception e) {
      throw new ServerException("Error in reading decision requirements", e);
    }
  }

  protected List<DecisionRequirements> searchFor(final SearchSourceBuilder searchSource) throws IOException {
    final SearchRequest searchRequest = new SearchRequest(decisionRequirementsIndex.getAlias()).source(searchSource);
    return ElasticsearchUtil.scroll(searchRequest, DecisionRequirements.class, objectMapper, elasticsearch);
  }

  @Override
  protected void buildFiltering(Query<DecisionRequirements> query, SearchSourceBuilder searchSourceBuilder) {
    final DecisionRequirements filter = query.getFilter();
    if (filter != null) {
      List<QueryBuilder> queryBuilders = new ArrayList<>();
      queryBuilders.add(buildTermQuery(DecisionRequirements.ID, filter.getId()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.KEY, filter.getKey()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.DECISION_REQUIREMENTS_ID, filter.getDecisionRequirementsId()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.NAME, filter.getName()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.VERSION, filter.getVersion()));
      queryBuilders.add(buildTermQuery(DecisionRequirements.RESOURCE_NAME, filter.getResourceName()));

      searchSourceBuilder.query(ElasticsearchUtil.joinWithAnd(queryBuilders.toArray(new QueryBuilder[] {})));
    }
  }
}
