/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.authentication.pt.PerTenantWebSessionRepositories;
import io.camunda.authentication.session.WebSessionMapper;
import io.camunda.authentication.session.WebSessionMapper.SpringBasedWebSessionAttributeConverter;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.clients.PersistentWebSessionClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.support.GenericConversionService;

/**
 * Per-tenant counterpart to {@link WebSessionRepositoryConfiguration} (gated on the {@code
 * pt-security} profile).
 *
 * <p>Iterates {@link PhysicalTenantResolver#getAll()} and produces one {@link
 * PersistentWebSessionClient} per tenant. The clients are exposed as a {@code Map<String,
 * PersistentWebSessionClient>} bean and consumed by {@link PerTenantWebSessionRepositories}, which
 * the {@code PhysicalTenantSecurityConfiguration} chains inject for their per-chain {@code
 * SessionRepositoryFilter}.
 *
 * <p>Storage isolation is structural: each tenant's repository is bound to its own client instance
 * — no shared backend, no key-prefixing decorator.
 *
 * <p><b>PoC backend choice.</b> Each tenant gets an {@link InMemoryPersistentWebSessionClient}. The
 * natural production substitution is a per-tenant {@code PersistentWebSessionRdbmsClient} (or
 * {@code PersistentWebSessionSearchImpl}) built from per-tenant {@code SqlSessionFactory} / {@code
 * SearchClients}. The data-source and search-clients primitives already exist per tenant; the
 * matching per-tenant MyBatis bundle (per-tenant {@code SqlSessionFactory} + {@code
 * PersistentWebSessionMapper}) does not yet exist in this branch. Wiring it up is a separate
 * refactor of {@code MyBatisConfiguration} and out of scope for the security PoC.
 *
 * <p>The {@code !pt-security} sibling {@link WebSessionRepositoryConfiguration} continues to
 * register the cluster-wide {@code WebSessionRepository}; it is excluded when this profile is
 * active, so there is no conflicting bean.
 */
@Configuration(proxyBeanMethods = false)
@Profile("pt-security")
@NullMarked
public class PhysicalTenantWebSessionRepositoryConfiguration {

  @Bean
  public Map<String, PersistentWebSessionClient> ptPersistentWebSessionClients(
      final PhysicalTenantResolver physicalTenantResolver) {
    final var clients = new LinkedHashMap<String, PersistentWebSessionClient>();
    physicalTenantResolver
        .getAll()
        .keySet()
        .forEach(tenantId -> clients.put(tenantId, new InMemoryPersistentWebSessionClient()));
    return Map.copyOf(clients);
  }

  @Bean
  public WebSessionMapper ptWebSessionMapper(final GenericConversionService conversionService) {
    return new WebSessionMapper(new SpringBasedWebSessionAttributeConverter(conversionService));
  }

  @Bean
  public PerTenantWebSessionRepositories perTenantWebSessionRepositories(
      final Map<String, PersistentWebSessionClient> ptPersistentWebSessionClients,
      final WebSessionMapper ptWebSessionMapper,
      final HttpServletRequest request) {
    return new PerTenantWebSessionRepositories(
        ptPersistentWebSessionClients, ptWebSessionMapper, request);
  }
}
