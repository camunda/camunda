/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.auth;

import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryBase;
import io.camunda.search.query.UserQuery;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import java.util.Map;

public final class AuthorizationQueryTransformers {
  private static final Map<Class<? extends SearchQueryBase>, AuthorizationQueryTransformer>
      TRANSFORMERS =
          Map.of(
              DecisionDefinitionQuery.class,
              new DecisionDefinitionAuthorizationQueryTransformer(),
              DecisionInstanceQuery.class,
              new DecisionInstanceAuthorizationQueryTransformer(),
              DecisionRequirementsQuery.class,
              new DecisionRequirementsAuthorizationQueryTransformer(),
              FlowNodeInstanceQuery.class,
              new FlowNodeInstanceAuthorizationQueryTransformer(),
              IncidentQuery.class,
              new IncidentAuthorizationQueryTransformer(),
              ProcessDefinitionQuery.class,
              new ProcessDefinitionAuthorizationQueryTransformer(),
              ProcessInstanceQuery.class,
              new ProcessInstanceAuthorizationQueryTransformer(),
              UserQuery.class,
              new UserAuthorizationQueryTransformer(),
              UserTaskQuery.class,
              new UserTaskAuthorizationQueryTransformer(),
              VariableQuery.class,
              new VariableAuthorizationQueryTransformer());

  private AuthorizationQueryTransformers() {}

  public static AuthorizationQueryTransformer getTransformer(
      final Class<? extends SearchQueryBase> clazz) {
    if (!TRANSFORMERS.containsKey(clazz)) {
      throw new IllegalArgumentException("Unsupported query type: %s".formatted(clazz));
    }
    return TRANSFORMERS.get(clazz);
  }
}
