/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.doubleOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.exists;
import static io.camunda.search.clients.query.SearchQueryBuilders.nestedQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.search.clients.query.SearchQueryBuilders.variableOperations;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.ClusterVariableFilter;
import io.camunda.search.filter.MetadataValueFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.authz.TenantCheck;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.ClusterVariableIndex;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableScope;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class ClusterVariableFilterTransformer
    extends IndexFilterTransformer<ClusterVariableFilter> {

  public ClusterVariableFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final ClusterVariableFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(getNamesQuery(filter.nameOperations()));
    queries.addAll(getValuesQuery(filter.valueOperations()));
    queries.addAll(getScopeQuery(filter.scopeOperations()));
    queries.addAll(getTenantQuery(filter.tenantIdOperations()));
    queries.addAll(getMetadataQuery(filter.metadataOperations()));
    queries.addAll(getKindQuery(filter.kindOperations()));
    ofNullable(getIsTruncatedQuery(filter.isTruncated())).ifPresent(queries::add);
    return and(queries);
  }

  @Override
  protected SearchQuery toTenantCheckSearchQuery(
      final TenantCheck tenantCheck, final String field) {
    final var tenantCheckQuery =
        Optional.of(tenantCheck)
            .map(TenantCheck::tenantIds)
            .filter(t -> !t.isEmpty())
            .map(t -> stringTerms(field, t))
            .orElse(null);

    final var matchGlobalQuery =
        term(ClusterVariableIndex.SCOPE, ClusterVariableScope.GLOBAL.name());

    return or(matchGlobalQuery, tenantCheckQuery);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(
      final RequiredAuthorization<?> authorization) {
    return stringTerms(ClusterVariableIndex.NAME, authorization.resourceIds());
  }

  private Collection<SearchQuery> getKindQuery(final List<Operation<String>> operations) {
    return stringOperations(ClusterVariableIndex.KIND, operations);
  }

  private Collection<SearchQuery> getNamesQuery(final List<Operation<String>> operations) {
    return stringOperations(ClusterVariableIndex.NAME, operations);
  }

  private Collection<SearchQuery> getTenantQuery(final List<Operation<String>> operations) {
    return stringOperations(ClusterVariableIndex.TENANT_ID, operations);
  }

  private Collection<SearchQuery> getScopeQuery(final List<Operation<String>> operations) {
    return stringOperations(ClusterVariableIndex.SCOPE, operations);
  }

  private List<SearchQuery> getValuesQuery(final List<UntypedOperation> variableFilters) {
    return variableOperations(ClusterVariableIndex.VALUE, variableFilters);
  }

  private SearchQuery getIsTruncatedQuery(final Boolean isTruncated) {
    if (isTruncated == null) {
      return null;
    }
    return term(ClusterVariableIndex.IS_PREVIEW, isTruncated);
  }

  private Collection<SearchQuery> getMetadataQuery(final List<MetadataValueFilter> filters) {
    if (filters == null || filters.isEmpty()) {
      return List.of();
    }
    return filters.stream().map(this::toMetadataNestedQuery).toList();
  }

  private SearchQuery toMetadataNestedQuery(final MetadataValueFilter filter) {
    final var keyQuery = term(ClusterVariableIndex.METADATA_KEY, filter.key());
    final var valueOperations = filter.valueOperations();
    if (valueOperations == null || valueOperations.isEmpty()) {
      return nestedQuery(ClusterVariableIndex.METADATA, keyQuery);
    }

    final var negated =
        valueOperations.stream().anyMatch(op -> op.operator() == Operator.NOT_EXISTS);
    if (negated) {
      if (valueOperations.size() > 1) {
        throw new IllegalArgumentException(
            "NOT_EXISTS cannot be combined with other operations for the same metadata key: "
                + filter.key());
      }
      return not(nestedQuery(ClusterVariableIndex.METADATA, keyQuery));
    }

    final var innerClauses = new ArrayList<SearchQuery>();
    innerClauses.add(keyQuery);
    innerClauses.addAll(toMetadataValueQueries(valueOperations));
    return nestedQuery(ClusterVariableIndex.METADATA, and(innerClauses));
  }

  private List<SearchQuery> toMetadataValueQueries(final List<UntypedOperation> operations) {
    final var stringOps = new ArrayList<Operation<String>>();
    final var numberOps = new ArrayList<Operation<Double>>();
    final var queries = new ArrayList<SearchQuery>();
    for (final var operation : operations) {
      if (operation.operator() == Operator.EXISTS) {
        // metadata.value is always populated (as a string representation) regardless of type;
        // metadata.valueNumber is only ever an additional, derived field for numeric values
        queries.add(exists(ClusterVariableIndex.METADATA_VALUE));
        continue;
      }
      switch (operation.type()) {
        // metadata.valueNumber is mapped as a double, so LONG and DOUBLE are both cast to Double
        case LONG, DOUBLE -> numberOps.add(toDoubleOperation(operation));
        // STRING and NULL live on the keyword field (booleans, documents, and arrays are
        // rejected at the REST layer, so they never reach this transformer)
        default -> stringOps.add(toStringOperation(operation));
      }
    }
    queries.addAll(stringOperations(ClusterVariableIndex.METADATA_VALUE, stringOps));
    queries.addAll(doubleOperations(ClusterVariableIndex.METADATA_VALUE_NUMBER, numberOps));
    return queries;
  }

  private static Operation<String> toStringOperation(final UntypedOperation operation) {
    final var values = operation.values().stream().map(String.class::cast).toList();
    return new Operation<>(operation.operator(), values);
  }

  private static Operation<Double> toDoubleOperation(final UntypedOperation operation) {
    final var values = operation.values().stream().map(v -> ((Number) v).doubleValue()).toList();
    return new Operation<>(operation.operator(), values);
  }
}
