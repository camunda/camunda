/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spi;

import io.camunda.gatekeeper.config.OidcConfig;
import java.util.List;
import java.util.Optional;

/**
 * SPI for providing OIDC authentication configurations at runtime.
 *
 * <p>The default implementation reads from Spring {@code @ConfigurationProperties}. Consumers can
 * provide a database-backed implementation to support dynamic IdP configuration (e.g., BYO IdP via
 * a UI panel).
 */
public interface OidcConfigurationProvider {

  /**
   * Returns all OIDC configurations.
   *
   * @return an unmodifiable list of OIDC configurations
   */
  List<OidcConfig> getConfigurations();

  /**
   * Returns the OIDC configuration for the given registration ID.
   *
   * @param registrationId the registration ID to look up
   * @return the configuration, or empty if not found
   */
  Optional<OidcConfig> getConfiguration(String registrationId);
}
