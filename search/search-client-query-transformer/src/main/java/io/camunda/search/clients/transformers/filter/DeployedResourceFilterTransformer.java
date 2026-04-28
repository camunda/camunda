/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.intOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.index.DeployedResourceIndex.DEPLOYMENT_KEY;
import static io.camunda.webapps.schema.descriptors.index.DeployedResourceIndex.RESOURCE_ID;
import static io.camunda.webapps.schema.descriptors.index.DeployedResourceIndex.RESOURCE_KEY;
import static io.camunda.webapps.schema.descriptors.index.DeployedResourceIndex.RESOURCE_NAME;
import static io.camunda.webapps.schema.descriptors.index.DeployedResourceIndex.RESOURCE_TYPE;
import static io.camunda.webapps.schema.descriptors.index.DeployedResourceIndex.VERSION;
import static io.camunda.webapps.schema.descriptors.index.DeployedResourceIndex.VERSION_TAG;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.DeployedResourceFilter;
import io.camunda.search.filter.Operation;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;

public class DeployedResourceFilterTransformer
    extends IndexFilterTransformer<DeployedResourceFilter> {

  public DeployedResourceFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final DeployedResourceFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    of(longOperations(RESOURCE_KEY, filter.resourceKeyOperations())).ifPresent(queries::addAll);
    of(getResourceNamesQuery(filter.resourceNameOperations())).ifPresent(queries::addAll);
    of(getResourceIdsQuery(filter.resourceIdOperations())).ifPresent(queries::addAll);
    of(getResourceTypesQuery(filter.resourceTypeOperations())).ifPresent(queries::addAll);
    of(getVersionsQuery(filter.versionOperations())).ifPresent(queries::addAll);
    of(getVersionTagsQuery(filter.versionTagOperations())).ifPresent(queries::addAll);
    of(longOperations(DEPLOYMENT_KEY, filter.deploymentKeyOperations())).ifPresent(queries::addAll);
    ofNullable(stringTerms(TENANT_ID, filter.tenantIds())).ifPresent(queries::add);
    return and(queries);
  }

  private List<SearchQuery> getResourceNamesQuery(final List<Operation<String>> resourceNames) {
    return stringOperations(RESOURCE_NAME, resourceNames);
  }

  private List<SearchQuery> getResourceIdsQuery(final List<Operation<String>> resourceIds) {
    return stringOperations(RESOURCE_ID, resourceIds);
  }

  private List<SearchQuery> getResourceTypesQuery(final List<Operation<String>> resourceTypes) {
    return stringOperations(RESOURCE_TYPE, resourceTypes);
  }

  private List<SearchQuery> getVersionsQuery(final List<Operation<Integer>> versions) {
    return intOperations(VERSION, versions);
  }

  private List<SearchQuery> getVersionTagsQuery(final List<Operation<String>> versionTags) {
    return stringOperations(VERSION_TAG, versionTags);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(RESOURCE_ID, authorization.resourceIds());
  }
}
