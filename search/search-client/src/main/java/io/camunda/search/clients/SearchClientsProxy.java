/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.clients.impl.NoopSearchClientsProxy;
import io.camunda.security.auth.SecurityContext;

public interface SearchClientsProxy
    extends AuthorizationSearchClient,
        DecisionDefinitionSearchClient,
        DecisionInstanceSearchClient,
        DecisionRequirementSearchClient,
        FlowNodeInstanceSearchClient,
        FormSearchClient,
        IncidentSearchClient,
        ProcessDefinitionSearchClient,
        ProcessInstanceSearchClient,
        RoleSearchClient,
        TenantSearchClient,
        UserTaskSearchClient,
        UserSearchClient,
        VariableSearchClient,
        MappingRuleSearchClient,
        GroupSearchClient,
        UsageMetricsSearchClient,
        BatchOperationSearchClient,
        SequenceFlowSearchClient,
        JobSearchClient,
        MessageSubscriptionSearchClient,
        CorrelatedMessagesSearchClient {

  @Override
  SearchClientsProxy withSecurityContext(SecurityContext securityContext);

  /**
   * Creates a no-op search client proxy. For usage in test environments where search is not
   * available.
   */
  static SearchClientsProxy noop() {
    return new NoopSearchClientsProxy();
  }
}
