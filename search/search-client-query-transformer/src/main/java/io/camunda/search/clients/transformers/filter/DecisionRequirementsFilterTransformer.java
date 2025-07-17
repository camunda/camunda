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
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.DECISION_REQUIREMENTS_ID;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.RESOURCE_NAME;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.VERSION;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public final class DecisionRequirementsFilterTransformer
    extends IndexFilterTransformer<DecisionRequirementsFilter> {

  public DecisionRequirementsFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final DecisionRequirementsFilter filter) {
    return and(
        longTerms(KEY, filter.decisionRequirementsKeys()),
        stringTerms(NAME, filter.names()),
        intTerms(VERSION, filter.versions()),
        stringTerms(DECISION_REQUIREMENTS_ID, filter.decisionRequirementsIds()),
        stringTerms(TENANT_ID, filter.tenantIds()),
        stringTerms(RESOURCE_NAME, filter.resourceNames()));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(DECISION_REQUIREMENTS_ID, authorization.resourceIds());
  }
}
