/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;

/**
 * Builds the {@link EngineSecurityConfig} of a physical tenant. The gateway, the broker and the
 * REST services all use it, so a field added to {@link EngineSecurityConfig} reaches every path.
 */
public final class EngineSecurityConfigFactory {

  private EngineSecurityConfigFactory() {}

  /**
   * @param security the tenant's resolved security configuration, for example {@code
   *     camunda.getSecurity()} of a tenant from {@code PhysicalTenantResolver}
   */
  public static EngineSecurityConfig create(final CamundaSecurityLibraryProperties security) {
    return new EngineSecurityConfig(
        security.getAuthentication(),
        security.getAuthorizations().isEnabled(),
        security.getMultiTenancy().isChecksEnabled(),
        security.getInitialization(),
        security.getCompiledIdValidationPattern(),
        security.getCompiledGroupIdValidationPattern());
  }
}
