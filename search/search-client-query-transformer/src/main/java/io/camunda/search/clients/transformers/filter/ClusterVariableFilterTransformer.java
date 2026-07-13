/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.exists;
import static io.camunda.search.clients.query.SearchQueryBuilders.gt;
import static io.camunda.search.clients.query.SearchQueryBuilders.gte;
import static io.camunda.search.clients.query.SearchQueryBuilders.lt;
import static io.camunda.search.clients.query.SearchQueryBuilders.lte;
import static io.camunda.search.clients.query.SearchQueryBuilders.nestedQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.objectTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.search.clients.query.SearchQueryBuilders.variableOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.wildcardQuery;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.entities.ValueTypeEnum;
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
    for (final UntypedOperation operation : valueOperations) {
      innerClauses.add(toMetadataValueQuery(operation));
    }
    return nestedQuery(ClusterVariableIndex.METADATA, and(innerClauses));
  }

  private SearchQuery toMetadataValueQuery(final UntypedOperation operation) {
    final var numeric =
        operation.type() == ValueTypeEnum.LONG || operation.type() == ValueTypeEnum.DOUBLE;
    final var field =
        numeric ? ClusterVariableIndex.METADATA_VALUE_NUMBER : ClusterVariableIndex.METADATA_VALUE;
    return switch (operation.operator()) {
      case EQUALS -> term(field, TypedValue.toTypedValue(operation.value()));
      case NOT_EQUALS -> not(term(field, TypedValue.toTypedValue(operation.value())));
      case EXISTS -> exists(field);
      case IN -> objectTerms(field, operation.values());
      case NOT_IN -> not(objectTerms(field, operation.values()));
      case GREATER_THAN -> gt(field, operation.value());
      case GREATER_THAN_EQUALS -> gte(field, operation.value());
      case LOWER_THAN -> lt(field, operation.value());
      case LOWER_THAN_EQUALS -> lte(field, operation.value());
      // LIKE operators only work for string fields i.e. only on METADATA_VALUE
      case LIKE -> wildcardQuery(ClusterVariableIndex.METADATA_VALUE, (String) operation.value());
      case NOT_EXISTS -> not(exists(field));
    };
  }
}
