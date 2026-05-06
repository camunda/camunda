/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

/**
 * Eagerly resolves a {@link Camunda} configuration per discovered physical tenant.
 *
 * <p>Each tenant {@code Camunda} is produced by:
 *
 * <ol>
 *   <li>creating a fresh {@link Camunda} instance,
 *   <li>binding {@code camunda.*} into it (so non-overridden values match the root resolution),
 *   <li>binding {@code camunda.physical-tenants.<tenantId>.*} into it (so tenant-specific keys
 *       override the seeded values).
 * </ol>
 *
 * <p>Legacy property fallback for non-overridden properties is preserved: it lives in the existing
 * {@code Camunda} getters via {@code UnifiedConfigurationHelper}, which reads from the global
 * environment at getter-call time. Because step 2 produces the same field state as the root binding
 * for any property the tenant did not override, those getters resolve legacy properties exactly as
 * they would on the root {@code Camunda}.
 *
 * <p>If no {@value #DEFAULT_PHYSICAL_TENANT_ID} tenant is declared under {@code
 * camunda.physical-tenants.*}, an entry under that key is synthesized from the root configuration
 * so that consumers can always address the root configuration as a tenant. An explicit {@code
 * default} declaration is honored as-is.
 */
@Component
public class PhysicalTenantResolver {

  public static final String DEFAULT_PHYSICAL_TENANT_ID = "default";

  private final ConfigurableEnvironment environment;
  private final PhysicalTenantDiscovery discovery = new PhysicalTenantDiscovery();
  private Map<String, Camunda> resolved = Collections.emptyMap();
  private final Camunda camunda;

  public PhysicalTenantResolver(final ConfigurableEnvironment environment, final Camunda camunda) {
    this.environment = environment;
    this.camunda = camunda;
  }

  @PostConstruct
  public void init() {
    final Map<String, Camunda> out = new LinkedHashMap<>();
    final Binder binder = Binder.get(environment);
    for (final String tenantId : discovery.discover(environment)) {
      final Camunda tenant = new Camunda();
      binder.bind(Camunda.PREFIX, Bindable.ofInstance(tenant));
      binder.bind(
          PhysicalTenantDiscovery.PHYSICAL_TENANTS_PREFIX + "." + tenantId,
          Bindable.ofInstance(tenant));
      out.put(tenantId, tenant);
    }
    if (!out.containsKey(DEFAULT_PHYSICAL_TENANT_ID)) {
      out.put(DEFAULT_PHYSICAL_TENANT_ID, camunda);
    }
    resolved = Collections.unmodifiableMap(out);
  }

  public Map<String, Camunda> resolved() {
    return resolved;
  }

  public <T> Map<String, T> mapValues(final Function<Camunda, T> mapper) {
    final Map<String, T> out = new LinkedHashMap<>();
    resolved.forEach((tenantId, camunda) -> out.put(tenantId, mapper.apply(camunda)));
    return Collections.unmodifiableMap(out);
  }

  public Camunda forPhysicalTenant(final String physicalTenantId) {
    return resolved.get(physicalTenantId);
  }
}
