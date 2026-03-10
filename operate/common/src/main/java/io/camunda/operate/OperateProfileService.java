/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import io.camunda.auth.domain.model.AuthenticationMethod;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class OperateProfileService {
  public static final String CONSOLIDATED_AUTH = "consolidated-auth";

  @Autowired private Environment environment;

  public String getMessageByProfileFor(final Exception exception) {
    if (exception != null && isDevelopmentProfileActive()) {
      return exception.getMessage();
    }
    return "";
  }

  public boolean isDevelopmentProfileActive() {
    return List.of(environment.getActiveProfiles()).contains("dev");
  }

  public boolean isConsolidatedAuthOidc() {
    // Support both old and new property names
    var methodValue = environment.getProperty("camunda.auth.method");
    if (methodValue == null) {
      methodValue = environment.getProperty("camunda.security.authentication.method");
    }
    final var consolidatedAuthVariation = AuthenticationMethod.parse(methodValue);

    return consolidatedAuthVariation.isPresent()
        && AuthenticationMethod.OIDC.equals(consolidatedAuthVariation.get());
  }

  public boolean isLoginDelegated() {
    return isConsolidatedAuthOidc();
  }
}
