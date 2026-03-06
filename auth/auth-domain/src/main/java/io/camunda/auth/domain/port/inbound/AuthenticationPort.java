/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.port.inbound;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Inbound port for authentication operations. */
public interface AuthenticationPort {

  /** Returns the current authenticated username, if any. */
  Optional<String> getCurrentUsername();

  /** Returns the current authenticated client ID, if any (for M2M flows). */
  Optional<String> getCurrentClientId();

  /** Returns the current bearer token, if available. */
  Optional<String> getCurrentToken();

  /** Returns the group IDs associated with the current authenticated principal. */
  List<String> getCurrentGroupIds();

  /** Returns the role IDs associated with the current authenticated principal. */
  List<String> getCurrentRoleIds();

  /** Returns the tenant IDs associated with the current authenticated principal. */
  List<String> getCurrentTenantIds();

  /** Returns the claims from the current authentication token. */
  Map<String, Object> getCurrentClaims();

  /** Returns whether the current user is authenticated. */
  boolean isAuthenticated();
}
