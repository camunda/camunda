/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.elasticsearch;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.api.v1.dao.DecisionDefinitionDao;
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component("ElasticsearchDecisionDefinitionDaoV1")
public class ElasticsearchDecisionDefinitionDao extends ElasticsearchDao<DecisionDefinition>
    implements DecisionDefinitionDao {

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired private DecisionRequirementsDao decisionRequirementsDao;

  @Override
  public DecisionDefinition byKey(final Long key) throws APIException {
    final List<DecisionDefinition> decisionDefinitions;
    try {
      decisionDefinitions = searchFor(ElasticsearchUtil.termsQuery(DecisionIndex.KEY, key));
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in reading decision definition for key %s", key), e);
    }
    if (decisionDefinitions.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format("No decision definition found for key %s", key));
    }
    if (decisionDefinitions.size() > 1) {
      throw new ServerException(
          String.format("Found more than one decision definition for key %s", key));
    }

    final DecisionDefinition decisionDefinition = decisionDefinitions.get(0);
    final DecisionRequirements decisionRequirements =
        decisionRequirementsDao.byKey(decisionDefinition.getDecisionRequirementsKey());
    decisionDefinition.setDecisionRequirementsName(decisionRequirements.getName());
    decisionDefinition.setDecisionRequirementsVersion(decisionRequirements.getVersion());

    return decisionDefinition;
  }

  @Override
  public Results<DecisionDefinition> search(final Query<DecisionDefinition> query)
      throws APIException {

    final var searchRequestBuilder =
        buildQueryOn(query, DecisionDefinition.KEY, new SearchRequest.Builder(), true);
    try {
      final var searchReq = searchRequestBuilder.index(decisionIndex.getAlias()).build();
      final var decisionDefinitions = searchWithResultsReturn(searchReq, DecisionDefinition.class);
      populateDecisionRequirementsNameAndVersion(decisionDefinitions.getItems());

      return decisionDefinitions;
    } catch (final Exception e) {
      throw new ServerException("Error in reading decision definitions", e);
    }
  }

  protected List<DecisionDefinition> searchFor(
      final co.elastic.clients.elasticsearch._types.query_dsl.Query query) {
    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequestBuilder =
        new SearchRequest.Builder().index(decisionIndex.getAlias()).query(tenantAwareQuery);

    return ElasticsearchUtil.scrollAllStream(
            es8Client, searchRequestBuilder, DecisionDefinition.class)
        .flatMap(res -> res.hits().hits().stream())
        .map(Hit::source)
        .toList();
  }

  @Override
  protected void buildFiltering(
      final Query<DecisionDefinition> query,
      final SearchRequest.Builder searchRequestBuilder,
      final boolean isTenantAware) {
    final DecisionDefinition filter = query.getFilter();
    if (filter == null) {
      return;
    }

    final var idQ =
        buildIfPresent(DecisionDefinition.ID, filter.getId(), ElasticsearchUtil::termsQuery);
    final var keyQ =
        buildIfPresent(DecisionDefinition.KEY, filter.getKey(), ElasticsearchUtil::termsQuery);
    final var decIdQ =
        buildIfPresent(
            DecisionDefinition.DECISION_ID, filter.getDecisionId(), ElasticsearchUtil::termsQuery);

    final var tenantIdQ =
        buildIfPresent(
            DecisionDefinition.TENANT_ID, filter.getTenantId(), ElasticsearchUtil::termsQuery);

    final var nameQ =
        buildIfPresent(DecisionDefinition.NAME, filter.getName(), ElasticsearchUtil::termsQuery);

    final var verQ =
        buildIfPresent(
            DecisionDefinition.VERSION, filter.getVersion(), ElasticsearchUtil::termsQuery);

    final var decReqIdQ =
        buildIfPresent(
            DecisionDefinition.DECISION_REQUIREMENTS_ID,
            filter.getDecisionRequirementsId(),
            ElasticsearchUtil::termsQuery);

    final var decReqKeyQ =
        buildIfPresent(
            DecisionDefinition.DECISION_REQUIREMENTS_KEY,
            filter.getDecisionRequirementsKey(),
            ElasticsearchUtil::termsQuery);

    final var filteringQ =
        buildFilteringByEs8(
            filter.getDecisionRequirementsName(), filter.getDecisionRequirementsVersion());

    final var andOfAllQueries =
        ElasticsearchUtil.joinWithAnd(
            idQ, keyQ, decIdQ, tenantIdQ, nameQ, verQ, decReqIdQ, decReqKeyQ, filteringQ);

    final var finalQuery =
        isTenantAware ? tenantHelper.makeQueryTenantAware(andOfAllQueries) : andOfAllQueries;

    searchRequestBuilder.query(finalQuery);
  }

  /**
   * buildFilteringBy
   *
   * @return the query to filter decision definitions by decisionRequirementsName and
   *     decisionRequirementsVersion, or null if no filter is needed
   */
  private co.elastic.clients.elasticsearch._types.query_dsl.Query buildFilteringByEs8(
      final String decisionRequirementsName, final Integer decisionRequirementsVersion) {

    final var nameQuery =
        decisionRequirementsName == null
            ? null
            : ElasticsearchUtil.termsQuery(
                DecisionRequirementsIndex.NAME, decisionRequirementsName);
    final var versionQuery =
        decisionRequirementsVersion == null
            ? null
            : ElasticsearchUtil.termsQuery(
                DecisionRequirementsIndex.VERSION, decisionRequirementsVersion);

    final var query = ElasticsearchUtil.joinWithAnd(nameQuery, versionQuery);

    if (query == null) {
      return null;
    }

    final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

    final var searchRequestBuilder =
        new SearchRequest.Builder()
            .index(decisionRequirementsIndex.getAlias())
            .query(tenantAwareQuery)
            .source(s -> s.filter(f -> f.includes(DecisionRequirementsIndex.KEY)));

    final var nonNullKeys =
        ElasticsearchUtil.scrollAllStream(
                es8Client, searchRequestBuilder, DecisionRequirements.class)
            .flatMap(res -> res.hits().hits().stream())
            .map(Hit::source)
            .filter(Objects::nonNull)
            .map(DecisionRequirements::getKey)
            .toList();

    if (nonNullKeys.isEmpty()) {
      return ElasticsearchUtil.createMatchNoneQueryEs8().build()._toQuery();
    }

    return ElasticsearchUtil.termsQuery(DecisionDefinition.DECISION_REQUIREMENTS_KEY, nonNullKeys);
  }

  /**
   * populateDecisionRequirementsNameAndVersion - adds decisionRequirementsName and
   * decisionRequirementsVersion fields to the decision definitions
   */
  private void populateDecisionRequirementsNameAndVersion(
      final List<DecisionDefinition> decisionDefinitions) {
    final Set<Long> decisionRequirementsKeys =
        decisionDefinitions.stream()
            .map(DecisionDefinition::getDecisionRequirementsKey)
            .collect(Collectors.toSet());
    final List<DecisionRequirements> decisionRequirements =
        decisionRequirementsDao.byKeys(decisionRequirementsKeys);

    final Map<Long, DecisionRequirements> decisionReqMap = new HashMap<>();
    decisionRequirements.forEach(
        decisionReq -> decisionReqMap.put(decisionReq.getKey(), decisionReq));
    decisionDefinitions.forEach(
        decisionDef -> {
          final DecisionRequirements decisionReq =
              (decisionDef.getDecisionRequirementsKey() == null)
                  ? null
                  : decisionReqMap.get(decisionDef.getDecisionRequirementsKey());
          if (decisionReq != null) {
            decisionDef.setDecisionRequirementsName(decisionReq.getName());
            decisionDef.setDecisionRequirementsVersion(decisionReq.getVersion());
          }
        });
  }
}
