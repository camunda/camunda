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
import io.camunda.operate.webapp.api.v1.dao.DecisionRequirementsDao;
import io.camunda.operate.webapp.api.v1.entities.DecisionRequirements;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.exceptions.APIException;
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.api.v1.exceptions.ServerException;
import io.camunda.operate.webapp.opensearch.OpensearchQueryDSLWrapper;
import io.camunda.operate.webapp.opensearch.OpensearchRequestDSLWrapper;
import io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex;
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
public class OpensearchDecisionRequirementsDao
    extends OpensearchKeyFilteringDao<DecisionRequirements, DecisionRequirements>
    implements DecisionRequirementsDao {

  private final DecisionRequirementsIndex decisionRequirementsIndex;

  public OpensearchDecisionRequirementsDao(
      final OpensearchQueryDSLWrapper queryDSLWrapper,
      final OpensearchRequestDSLWrapper requestDSLWrapper,
      final RichOpenSearchClient richOpenSearchClient,
      final DecisionRequirementsIndex decisionRequirementsIndex) {
    super(queryDSLWrapper, requestDSLWrapper, richOpenSearchClient);
    this.decisionRequirementsIndex = decisionRequirementsIndex;
  }

  @Override
  protected String getKeyFieldName() {
    return DecisionRequirements.KEY;
  }

  @Override
  protected String getByKeyServerReadErrorMessage(final Long key) {
    return String.format("Error in reading decision requirements for key %s", key);
  }

  @Override
  protected String getByKeyNoResultsErrorMessage(final Long key) {
    return String.format("No decision requirements found for key %s", key);
  }

  @Override
  protected String getByKeyTooManyResultsErrorMessage(final Long key) {
    return String.format("Found more than one decision requirements for key %s", key);
  }

  @Override
  public List<DecisionRequirements> byKeys(final Set<Long> keys) throws APIException {
    final List<Long> nonNullKeys =
        (keys == null) ? List.of() : keys.stream().filter(Objects::nonNull).toList();
    if (nonNullKeys.isEmpty()) {
      return List.of();
    }
    try {
      final var request =
          requestDSLWrapper
              .searchRequestBuilder(getIndexName())
              .query(queryDSLWrapper.longTerms(getKeyFieldName(), nonNullKeys));
      return richOpenSearchClient.doc().scrollValues(request, DecisionRequirements.class);
    } catch (final Exception e) {
      throw new ServerException("Error in reading decision requirements by keys", e);
    }
  }

  @Override
  public String xmlByKey(final Long key) throws APIException {
    validateKey(key);
    final var request =
        requestDSLWrapper
            .searchRequestBuilder(getIndexName())
            .query(
                queryDSLWrapper.withTenantCheck(
                    queryDSLWrapper.term(DecisionRequirements.KEY, key)))
            .source(queryDSLWrapper.sourceInclude(DecisionRequirementsIndex.XML));
    try {
      final var response = richOpenSearchClient.doc().search(request, Map.class);
      if (response.hits().total().value() == 1) {
        return response.hits().hits().get(0).source().get(DecisionRequirementsIndex.XML).toString();
      }
    } catch (final Exception e) {
      throw new ServerException(
          String.format("Error in reading decision requirements as xml for key %s", key), e);
    }
    throw new ResourceNotFoundException(
        String.format("Decision requirements for key %s not found.", key));
  }

  @Override
  protected String getUniqueSortKey() {
    return DecisionRequirements.KEY;
  }

  @Override
  protected Class<DecisionRequirements> getInternalDocumentModelClass() {
    return DecisionRequirements.class;
  }

  @Override
  protected String getIndexName() {
    return decisionRequirementsIndex.getAlias();
  }

  @Override
  protected void buildFiltering(
      final Query<DecisionRequirements> query, final SearchRequest.Builder request) {
    final DecisionRequirements filter = query.getFilter();
    if (filter != null) {
      final var queryTerms =
          Stream.of(
                  queryDSLWrapper.term(DecisionRequirements.ID, filter.getId()),
                  queryDSLWrapper.term(DecisionRequirements.KEY, filter.getKey()),
                  queryDSLWrapper.term(
                      DecisionRequirements.DECISION_REQUIREMENTS_ID,
                      filter.getDecisionRequirementsId()),
                  queryDSLWrapper.term(DecisionRequirements.TENANT_ID, filter.getTenantId()),
                  queryDSLWrapper.term(DecisionRequirements.NAME, filter.getName()),
                  queryDSLWrapper.term(DecisionRequirements.VERSION, filter.getVersion()),
                  queryDSLWrapper.term(
                      DecisionRequirements.RESOURCE_NAME, filter.getResourceName()))
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!queryTerms.isEmpty()) {
        request.query(queryDSLWrapper.and(queryTerms));
      }
    }
  }

  @Override
  protected DecisionRequirements convertInternalToApiResult(
      final DecisionRequirements internalResult) {
    return internalResult;
  }
}
