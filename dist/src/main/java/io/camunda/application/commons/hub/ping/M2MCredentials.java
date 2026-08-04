/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.hub.ping;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record M2MCredentials(
    URI tokenEndpoint,
    String clientId,
    String clientSecret,
    Map<String, String> tokenRequestParameters) {
  private static final Set<String> RESERVED_PARAMETERS =
      Set.of("grant_type", "client_id", "client_secret");

  public M2MCredentials {
    tokenRequestParameters =
        tokenRequestParameters == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(tokenRequestParameters));
  }

  public Optional<String> tokenRequestParametersValidationError() {
    for (final String name : tokenRequestParameters.keySet()) {
      if (name == null || name.isBlank()) {
        return Optional.of("Token request parameter names must not be blank.");
      }
      if (RESERVED_PARAMETERS.contains(name)) {
        return Optional.of(
            "Token request parameter '%s' is managed by Camunda and cannot be overridden."
                .formatted(name));
      }
    }
    return Optional.empty();
  }

  @Override
  public String toString() {
    return "M2MCredentials[tokenEndpoint="
        + tokenEndpoint
        + ", clientId="
        + clientId
        + ", clientSecret=***, tokenRequestParameterNames="
        + tokenRequestParameters.keySet()
        + "]";
  }
}
