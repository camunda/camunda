/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.authentication.session.WebSessionMapper;
import io.camunda.authentication.session.WebSessionMapper.SpringBasedWebSessionAttributeConverter;
import io.camunda.authentication.session.WebSessionRepository;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
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
 * <p>Iterates {@link PhysicalTenantResolver#getAll()} and produces one {@link WebSessionRepository}
 * per tenant, exposed as a {@code Map<String, WebSessionRepository>} keyed by tenant id. The map is
 * injected directly into {@code PhysicalTenantSecurityConfiguration}, which looks the right
 * instance up per chain.
 *
 * <p>Storage isolation is structural: each tenant's repository owns its own backing client — no
 * shared backend, no key-prefixing decorator.
 *
 * <p><b>PoC scope:</b> the backing client for each tenant is an in-memory {@link
 * InMemoryPersistentWebSessionClient}. The PoC deliberately does not wire per-tenant durable
 * secondary storage; that's a separate concern from the per-tenant security wiring this PoC
 * validates. Sessions live for the lifetime of the process.
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
  public Map<String, WebSessionRepository> ptWebSessionRepositories(
      final PhysicalTenantResolver physicalTenantResolver,
      final GenericConversionService conversionService,
      final HttpServletRequest request) {
    final var mapper =
        new WebSessionMapper(new SpringBasedWebSessionAttributeConverter(conversionService));
    final var repositories = new LinkedHashMap<String, WebSessionRepository>();
    physicalTenantResolver
        .getAll()
        .keySet()
        .forEach(
            tenantId ->
                repositories.put(
                    tenantId,
                    new WebSessionRepository(
                        new InMemoryPersistentWebSessionClient(), mapper, request)));
    return Map.copyOf(repositories);
  }
}
