/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.search.clients.reader.PhysicalTenantSearchClientReaders;
import io.camunda.security.core.authz.AuthorizationChecker;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the single {@link AuthorizationCheckerProvider} injected into the service registry.
 *
 * <p>Gated on the same condition as its only consumer ({@link
 * io.camunda.application.commons.service.CamundaServicesConfiguration}) rather than on secondary
 * storage, so it constructs in every storage mode: the required, default-tenant-pinned {@link
 * AuthorizationChecker} bean is always present here, while the per-tenant checkers are derived only
 * when {@link PhysicalTenantSearchClientReaders} exists (i.e. secondary storage is enabled). Each
 * per-tenant checker is built through {@link AuthorizationCheckerFactory}, the same factory {@link
 * io.camunda.application.commons.search.ResourceAccessControllerConfiguration} uses, so the two
 * checker-construction sites stay in sync.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
public class AuthorizationCheckerProviderConfiguration {

  @Bean
  public AuthorizationCheckerProvider authorizationCheckerProvider(
      final AuthorizationChecker defaultChecker,
      final Optional<PhysicalTenantSearchClientReaders> physicalTenantSearchClientReaders) {
    final Map<String, AuthorizationChecker> checkers = new LinkedHashMap<>();
    physicalTenantSearchClientReaders.ifPresent(
        readers ->
            readers
                .readersByPhysicalTenant()
                .forEach(
                    (tenantId, searchClientReaders) ->
                        checkers.put(
                            tenantId,
                            AuthorizationCheckerFactory.forPhysicalTenant(searchClientReaders))));
    return new AuthorizationCheckerProvider(defaultChecker, Map.copyOf(checkers));
  }
}
