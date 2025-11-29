/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.auth.condition;

import io.camunda.security.auth.Authorization;
import java.util.Objects;

/**
 * An {@link AuthorizationCondition} that requires a single {@link Authorization} to be evaluated.
 */
public record SingleAuthorizationCondition(Authorization<?> authorization)
    implements AuthorizationCondition {

  public SingleAuthorizationCondition {
    Objects.requireNonNull(authorization, "SingleAuthorizationCondition requires an authorization");
  }
}
