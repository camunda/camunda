/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Wrapper around ClientRegistrationRepository that ensures repository is created rather at a
 * startup or during later retries.
 */
public class SafeInitClientRegistrationRepository implements ClientRegistrationRepository {

  private static final Logger LOG =
      LoggerFactory.getLogger(SafeInitClientRegistrationRepository.class);

  /**
   * Helper that ensures ClientRegistrationRepository is created rather on startup or after retries
   * if startup initialization failed.
   */
  private final SafeInitProxy<ClientRegistrationRepository> proxy;

  public SafeInitClientRegistrationRepository(
      final Supplier<ClientRegistrationRepository> repositoryFactory) {
    // Try to create a repository. In case of failure - schedule async retries of creation.
    proxy =
        new SafeInitProxy<>(
            repositoryFactory,
            e -> LOG.warn("Failed to initialize ClientRegistrationRepository. Retrying.", e));
  }

  @Override
  public ClientRegistration findByRegistrationId(final String registrationId) {
    // if repository is initialized, then delegate method call to it. If not, throw an exception
    // that will be propagated to client.
    return proxy
        .orElseThrow(
            () ->
                new RuntimeException(
                    "Authentication service unavailable: Unable to connect to the configured Identity Provider (OIDC). "
                        + "Please try again later or contact your administrator."))
        .findByRegistrationId(registrationId);
  }
}
