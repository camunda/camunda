/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import co.elastic.clients.elasticsearch.core.search.Hit;
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
          searchFor(ElasticsearchUtil.termsQuery(DecisionRequirementsIndex.KEY, key));
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
      return searchFor(ElasticsearchUtil.termsQuery(DecisionRequirementsIndex.KEY, nonNullKeys));
    } catch (final Exception e) {
      throw new ServerException("Error in reading decision requirements by keys", e);
    }
  }

  @Override
  public String xmlByKey(final Long key) throws APIException {
    try {
      final var query = ElasticsearchUtil.termsQuery(DecisionRequirementsIndex.KEY, key);
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

      final var searchReq =
          new SearchRequest.Builder()
              .index(decisionRequirementsIndex.getAlias())
              .query(tenantAwareQuery)
              .source(s -> s.filter(f -> f.includes(DecisionRequirementsIndex.XML)));

      final var res = es8Client.search(searchReq.build(), ElasticsearchUtil.MAP_CLASS);

      if (res.hits().total().value() == 1) {
        return res.hits().hits().getFirst().source().get(DecisionRequirementsIndex.XML).toString();
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
    final var searchRequestBuilder =
        buildQueryOn(query, DecisionRequirements.KEY, new SearchRequest.Builder(), true);

    try {
      final var searchReq =
          searchRequestBuilder.index(decisionRequirementsIndex.getAlias()).build();

      return searchWithResultsReturn(searchReq, DecisionRequirements.class);
    } catch (final Exception e) {
      throw new ServerException("Error in reading decision requirements", e);
    }
  }

  protected List<DecisionRequirements> searchFor(
      final co.elastic.clients.elasticsearch._types.query_dsl.Query query) {
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchReqBuilder =
        new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
            .index(decisionRequirementsIndex.getAlias())
            .query(tenantAwareQuery);

    return ElasticsearchUtil.scrollAllStream(
            es8Client, searchReqBuilder, DecisionRequirements.class)
        .flatMap(res -> res.hits().hits().stream())
        .map(Hit::source)
        .toList();
  }

  @Override
  protected void buildFiltering(
      final Query<DecisionRequirements> query, final SearchSourceBuilder searchSourceBuilder) {}

  @Override
  protected void buildFiltering(
      final Query<DecisionRequirements> query,
      final Builder searchRequestBuilder,
      final boolean isTenantAware) {
    final DecisionRequirements filter = query.getFilter();
    if (filter == null) {
      return;
    }

    final var idQ =
        buildIfPresent(DecisionRequirements.ID, filter.getId(), ElasticsearchUtil::termsQuery);

    final var keyQ =
        buildIfPresent(DecisionRequirements.KEY, filter.getKey(), ElasticsearchUtil::termsQuery);

    final var decReqIdQ =
        buildIfPresent(
            DecisionRequirements.DECISION_REQUIREMENTS_ID,
            filter.getDecisionRequirementsId(),
            ElasticsearchUtil::termsQuery);

    final var tenantIdQ =
        buildIfPresent(
            DecisionRequirements.TENANT_ID, filter.getTenantId(), ElasticsearchUtil::termsQuery);

    final var nameQ =
        buildIfPresent(DecisionRequirements.NAME, filter.getName(), ElasticsearchUtil::termsQuery);

    final var versionQ =
        buildIfPresent(
            DecisionRequirements.VERSION, filter.getVersion(), ElasticsearchUtil::termsQuery);

    final var resourceNameQ =
        buildIfPresent(
            DecisionRequirements.RESOURCE_NAME,
            filter.getResourceName(),
            ElasticsearchUtil::termsQuery);

    final var andOfAllQueries =
        ElasticsearchUtil.joinWithAnd(
            idQ, keyQ, decReqIdQ, tenantIdQ, nameQ, versionQ, resourceNameQ);

    final var finalQuery =
        isTenantAware ? tenantHelper.makeQueryTenantAware(andOfAllQueries) : andOfAllQueries;

    searchRequestBuilder.query(finalQuery);
  }
}
