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
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.DECISION_REQUIREMENTS_NAME;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.DECISION_REQUIREMENTS_VERSION;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.DecisionIndex.VERSION;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public final class DecisionDefinitionFilterTransformer
    extends IndexFilterTransformer<DecisionDefinitionFilter> {

  public DecisionDefinitionFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(DECISION_ID, authorization.resourceIds());
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
        stringTerms(DECISION_REQUIREMENTS_NAME, filter.decisionRequirementsNames()),
        intTerms(DECISION_REQUIREMENTS_VERSION, filter.decisionRequirementsVersions()),
        stringTerms(TENANT_ID, filter.tenantIds()));
  }
}
