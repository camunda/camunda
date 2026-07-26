/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.search.clients.reader.PhysicalTenantSearchClientReaders;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.security.impl.SearchAuthorizationScopeRepository;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds one {@link AuthorizationChecker} per physical tenant, each backed by that tenant's own
 * {@link io.camunda.search.clients.reader.AuthorizationReader}, mirroring the per-tenant {@code
 * ResourceAccessController} construction in {@link
 * io.camunda.application.commons.search.ResourceAccessControllerConfiguration}.
 *
 * <p>This exists so that services which query {@link AuthorizationChecker} directly (bypassing the
 * {@code ResourceAccessController}/data-plane path), such as {@code DocumentServices} and {@code
 * SecretServices}, can be handed the checker for their own physical tenant instead of the single
 * default-tenant-pinned {@link AuthorizationChecker} bean.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageEnabled
public class PhysicalTenantAuthorizationCheckerConfiguration {

  @Bean
  public PhysicalTenantAuthorizationCheckers physicalTenantAuthorizationCheckers(
      final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders) {
    final Map<String, AuthorizationChecker> checkers = new LinkedHashMap<>();
    physicalTenantSearchClientReaders
        .readersByPhysicalTenant()
        .forEach(
            (tenantId, searchClientReaders) ->
                checkers.put(
                    tenantId,
                    new AuthorizationChecker(
                        new SearchAuthorizationScopeRepository(
                            searchClientReaders.authorizationReader()))));
    return new PhysicalTenantAuthorizationCheckers(Map.copyOf(checkers));
  }
}
