/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.intTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.DECISION_ID;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.DECISION_REQUIREMENTS_ID;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.DECISION_REQUIREMENTS_KEY;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.VERSION;

import io.camunda.search.clients.control.ResourceAccessControl;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public final class DecisionDefinitionFilterTransformer
    extends IndexFilterTransformer<DecisionDefinitionFilter> {

  public DecisionDefinitionFilterTransformer(final IndexDescriptor indexDescriptor) {
    this(indexDescriptor, null);
  }

  public DecisionDefinitionFilterTransformer(
      final IndexDescriptor indexDescriptor, final ResourceAccessControl resourceAccessControl) {
    super(indexDescriptor, resourceAccessControl);
  }

  @Override
  public DecisionDefinitionFilterTransformer withResourceAccessControl(
      final ResourceAccessControl resourceAccessControl) {
    return new DecisionDefinitionFilterTransformer(indexDescriptor, resourceAccessControl);
  }

  @Override
  protected SearchQuery toAuthorizationSearchQuery(final Authorization authorization) {
    final var resourceIds = authorization.resourceIds();
    return stringTerms(DECISION_ID, resourceIds);
  }

  @Override
  protected SearchQuery toTenantSearchQuery(final List<String> tenantIds) {
    return stringTerms(TENANT_ID, tenantIds);
  }

  @Override
  public SearchQuery toSearchQuery(final DecisionDefinitionFilter filter) {
    return and(
        longTerms(KEY, filter.decisionDefinitionKeys()),
        stringTerms(DECISION_ID, filter.decisionDefinitionIds()),
        stringTerms(NAME, filter.names()),
        intTerms(VERSION, filter.versions()),
        stringTerms(DECISION_REQUIREMENTS_ID, filter.decisionRequirementsIds()),
        longTerms(DECISION_REQUIREMENTS_KEY, filter.decisionRequirementsKeys()),
        stringTerms(TENANT_ID, filter.tenantIds()));
  }
}
