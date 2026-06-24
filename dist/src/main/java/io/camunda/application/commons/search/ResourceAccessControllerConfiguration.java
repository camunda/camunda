/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.security.PhysicalTenantSecurityProperties;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.read.security.RdbmsResourceAccessController;
import io.camunda.search.clients.auth.AnonymousResourceAccessController;
import io.camunda.search.clients.auth.DefaultResourceAccessProvider;
import io.camunda.search.clients.auth.DefaultTenantAccessProvider;
import io.camunda.search.clients.auth.DisabledResourceAccessProvider;
import io.camunda.search.clients.auth.DisabledTenantAccessProvider;
import io.camunda.search.clients.auth.DocumentBasedResourceAccessController;
import io.camunda.search.clients.auth.ResourceAccessDelegatingController;
import io.camunda.search.clients.reader.PhysicalTenantSearchClientReaders;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.security.core.authz.ResourceAccessController;
import io.camunda.security.core.authz.ResourceAccessProvider;
import io.camunda.security.core.authz.TenantAccessProvider;
import io.camunda.security.impl.SearchAuthorizationScopeRepository;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@ConditionalOnSecondaryStorageEnabled
public class ResourceAccessControllerConfiguration {

  @Bean
  public ResourceAccessProvider resourceAccessProvider(
      final CamundaSecurityLibraryProperties cslProperties, final AuthorizationChecker checker) {
    return cslProperties.getAuthorizations().isEnabled()
        ? new DefaultResourceAccessProvider(checker)
        : new DisabledResourceAccessProvider();
  }

  @Bean
  public TenantAccessProvider tenantAccessProvider(
      final CamundaSecurityLibraryProperties cslProperties) {
    return cslProperties.getMultiTenancy().isChecksEnabled()
        ? new DefaultTenantAccessProvider()
        : new DisabledTenantAccessProvider();
  }

  @Bean
  @ConditionalOnSecondaryStorageType({
    SecondaryStorageType.elasticsearch,
    SecondaryStorageType.opensearch
  })
  public PhysicalTenantResourceAccessControllers
      documentBasedPhysicalTenantResourceAccessControllers(
          final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders,
          final PhysicalTenantSecurityProperties physicalTenantSecurityProperties,
          final TenantAccessProvider tenantAccessProvider) {
    return buildPerTenantControllers(
        physicalTenantSearchClientReaders,
        physicalTenantSecurityProperties,
        tenantAccessProvider,
        DocumentBasedResourceAccessController::new);
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
  public PhysicalTenantResourceAccessControllers rdbmsPhysicalTenantResourceAccessControllers(
      final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders,
      final PhysicalTenantSecurityProperties physicalTenantSecurityProperties,
      final TenantAccessProvider tenantAccessProvider) {
    return buildPerTenantControllers(
        physicalTenantSearchClientReaders,
        physicalTenantSecurityProperties,
        tenantAccessProvider,
        RdbmsResourceAccessController::new);
  }

  private static PhysicalTenantResourceAccessControllers buildPerTenantControllers(
      final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders,
      final PhysicalTenantSecurityProperties physicalTenantSecurityProperties,
      final TenantAccessProvider tenantAccessProvider,
      final BiFunction<ResourceAccessProvider, TenantAccessProvider, ResourceAccessController>
          controllerFactory) {
    final Map<String, ResourceAccessController> controllers = new LinkedHashMap<>();
    physicalTenantSearchClientReaders
        .readersByPhysicalTenant()
        .forEach(
            (tenantId, searchClientReaders) -> {
              final var cslProps =
                  physicalTenantSecurityProperties.propertiesByPhysicalTenant().get(tenantId);
              final var checker =
                  new AuthorizationChecker(
                      new SearchAuthorizationScopeRepository(
                          searchClientReaders.authorizationReader()));
              final ResourceAccessProvider provider =
                  cslProps.getAuthorizations().isEnabled()
                      ? new DefaultResourceAccessProvider(checker)
                      : new DisabledResourceAccessProvider();
              final ResourceAccessController resourceAccessController =
                  controllerFactory.apply(provider, tenantAccessProvider);
              controllers.put(
                  tenantId,
                  new ResourceAccessDelegatingController(
                      List.of(new AnonymousResourceAccessController(), resourceAccessController)));
            });
    return new PhysicalTenantResourceAccessControllers(Map.copyOf(controllers));
  }
}
