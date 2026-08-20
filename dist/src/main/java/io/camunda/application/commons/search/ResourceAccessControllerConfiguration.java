/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.security.PhysicalTenantSecurityProperties;
import io.camunda.authentication.service.PhysicalTenantResourceAccessProvider;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.read.security.RdbmsResourceAccessController;
import io.camunda.search.clients.auth.AnonymousResourceAccessController;
import io.camunda.search.clients.auth.DefaultResourceAccessProvider;
import io.camunda.search.clients.auth.DocumentBasedResourceAccessController;
import io.camunda.search.clients.auth.ResourceAccessDelegatingController;
import io.camunda.search.clients.reader.PhysicalTenantSearchClientReaders;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.security.core.authz.ResourceAccessController;
import io.camunda.security.core.authz.ResourceAccessProvider;
import io.camunda.security.core.authz.TenantAccessProvider;
import io.camunda.security.core.port.out.AuthorizationScopeRepositoryPort;
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
      final CamundaSecurityLibraryProperties cslProperties,
      final AuthorizationScopeRepositoryPort scopeRepository) {
    return DefaultResourceAccessProvider.forScopeRepository(
        scopeRepository, cslProperties.getAuthorizations().isEnabled());
  }

  @Bean
  public TenantAccessProvider tenantAccessProvider(
      final CamundaSecurityLibraryProperties cslProperties) {
    return TenantAccessProvider.of(cslProperties.getMultiTenancy().isChecksEnabled());
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

  /**
   * The {@link ResourceAccessProvider} scoped per physical tenant, consumed by the consolidated
   * {@code /v2/authentication/me} handlers to resolve {@code COMPONENT ACCESS} against the tenant
   * the request is scoped to (rather than the root-bound {@link #resourceAccessProvider} bean).
   *
   * <p>One entry per configured physical tenant (including the default one); there is deliberately
   * no shared-default fallback, so an unknown tenant fails hard rather than silently resolving
   * against the wrong tenant's storage.
   *
   * <p>Storage-agnostic: the per-tenant provider is built the same way for every secondary-storage
   * type, so unlike {@link PhysicalTenantResourceAccessControllers} it needs no storage-type split.
   */
  @Bean
  public PhysicalTenantResourceAccessProvider physicalTenantResourceAccessProvider(
      final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders,
      final PhysicalTenantSecurityProperties physicalTenantSecurityProperties) {
    final Map<String, ResourceAccessProvider> providers = new LinkedHashMap<>();
    physicalTenantSearchClientReaders
        .readersByPhysicalTenant()
        .forEach(
            (tenantId, searchClientReaders) -> {
              final var cslProps =
                  physicalTenantSecurityProperties.propertiesByPhysicalTenant().get(tenantId);
              providers.put(tenantId, resourceAccessProviderFor(searchClientReaders, cslProps));
            });
    return new PhysicalTenantResourceAccessProvider(Map.copyOf(providers));
  }

  private static ResourceAccessProvider resourceAccessProviderFor(
      final SearchClientReaders searchClientReaders,
      final CamundaSecurityLibraryProperties cslProps) {
    final var scopeRepository =
        new SearchAuthorizationScopeRepository(searchClientReaders.authorizationReader());
    return DefaultResourceAccessProvider.forScopeRepository(
        scopeRepository, cslProps.getAuthorizations().isEnabled());
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
              final ResourceAccessProvider provider =
                  resourceAccessProviderFor(searchClientReaders, cslProps);
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
