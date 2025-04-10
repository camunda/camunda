/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao.opensearch;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.webapp.api.v1.dao.DecisionDefinitionDao;
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionDefinition;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchDecisionDefinitionDao
    extends OpensearchKeyFilteringDao<DecisionDefinition, DecisionDefinition>
    implements DecisionDefinitionDao {

  private final DecisionIndex decisionIndex;

  private final DecisionRequirementsIndex decisionRequirementsIndex;

  private final DecisionRequirementsDao decisionRequirementsDao;

  public OpensearchDecisionDefinitionDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient,
      final DecisionIndex decisionIndex,
      final DecisionRequirementsIndex decisionRequirementsIndex,
      final DecisionRequirementsDao decisionRequirementsDao) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.decisionIndex = decisionIndex;
    this.decisionRequirementsIndex = decisionRequirementsIndex;
    this.decisionRequirementsDao = decisionRequirementsDao;
  }

  @Override
  public DecisionDefinition byKey(final Long key) {
    final var decisionDefinition = super.byKey(key);
    final DecisionRequirements decisionRequirements =
        decisionRequirementsDao.byKey(decisionDefinition.getDecisionRequirementsKey());
    decisionDefinition.setDecisionRequirementsName(decisionRequirements.getName());
    decisionDefinition.setDecisionRequirementsVersion(decisionRequirements.getVersion());
    return decisionDefinition;
  }

  @Override
  protected String getKeyFieldName() {
    return DecisionIndex.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(final Long key) {
    return String.format("Error in reading decision definition for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(final Long key) {
    return String.format("No decision definition found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(final Long key) {
    return String.format("Found more than one decision definition for key %s", key);
  }

  @Override
  public Results<DecisionDefinition> search(final Query<DecisionDefinition> query) {
    final var results = super.search(query);
    final var decisionDefinitions = results.getItems();
    populateDecisionRequirementsNameAndVersion(decisionDefinitions);
    return results;
  }

  @Override
  protected String getUniqueSortKey() {
    return DecisionDefinition.KEY;
  }

  @Override
  protected Class<DecisionDefinition> getInternalDocumentModelClass() {
    return DecisionDefinition.class;
  }

  @Override
  protected String getIndexName() {
    return decisionIndex.getAlias();
  }

  @Override
  protected void buildFiltering(
      final Query<DecisionDefinition> query, final SearchRequest.Builder request) {
    final DecisionDefinition filter = query.getFilter();

    if (filter != null) {
      final var queryTerms =
          Stream.of(
                  queryDSLWrapper.term(DecisionDefinition.ID, filter.getId()),
                  queryDSLWrapper.term(DecisionDefinition.KEY, filter.getKey()),
                  queryDSLWrapper.term(DecisionDefinition.DECISION_ID, filter.getDecisionId()),
                  queryDSLWrapper.term(DecisionDefinition.TENANT_ID, filter.getTenantId()),
                  queryDSLWrapper.term(DecisionDefinition.NAME, filter.getName()),
                  queryDSLWrapper.term(DecisionDefinition.VERSION, filter.getVersion()),
                  queryDSLWrapper.term(
                      DecisionDefinition.DECISION_REQUIREMENTS_ID,
                      filter.getDecisionRequirementsId()),
                  queryDSLWrapper.term(
                      DecisionDefinition.DECISION_REQUIREMENTS_KEY,
                      filter.getDecisionRequirementsKey()),
                  buildFilteringBy(
                      filter.getDecisionRequirementsName(),
                      filter.getDecisionRequirementsVersion()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected DecisionDefinition convertInternalToApiResult(final DecisionDefinition internalResult) {
    return internalResult;
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

  private org.opensearch.client.opensearch._types.query_dsl.Query buildFilteringBy(
      final String decisionRequirementsName, final Integer decisionRequirementsVersion) {
    try {
      final List<org.opensearch.client.opensearch._types.query_dsl.Query> queryTerms =
          new LinkedList<>();
      queryTerms.add(
          queryDSLWrapper.term(DecisionRequirementsIndex.NAME, decisionRequirementsName));
      queryTerms.add(
          queryDSLWrapper.term(DecisionRequirementsIndex.VERSION, decisionRequirementsVersion));
      final var query = queryDSLWrapper.and(queryTerms);
      if (query == null) {
        return null;
      }
      final var request =
          requestDSLWrapper
              .searchRequestBuilder(decisionRequirementsIndex.getAlias())
              .query(queryDSLWrapper.withTenantCheck(query))
              .source(queryDSLWrapper.sourceInclude(DecisionRequirementsIndex.KEY));
      final var decisionRequirements =
          richOpenSearchClient.doc().scrollValues(request, DecisionRequirements.class);
      final List<Long> nonNullKeys =
          decisionRequirements.stream()
              .map(DecisionRequirements::getKey)
              .filter(Objects::nonNull)
              .toList();
      if (nonNullKeys.isEmpty()) {
        return queryDSLWrapper.matchNone();
      }
      return queryDSLWrapper.longTerms(DecisionDefinition.DECISION_REQUIREMENTS_KEY, nonNullKeys);
    } catch (final Exception e) {
      throw new ServerException("Error in reading decision requirements by name and version", e);
    }
  }
}
