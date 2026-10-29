/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.application.commons.service.CamundaServicesConfiguration.PhysicalTenantServiceFactory;
import io.camunda.configuration.Camunda;
import io.camunda.service.registry.DefaultServiceRegistry;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.broker.PhysicalTenantResources;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ServiceRegistry} of the REST and MCP APIs, following the tenant lifecycle: a tenant
 * added or replaced at runtime gets services built from its configuration, exactly as a tenant of
 * the startup configuration does, and a removed tenant's services are dropped.
 *
 * <p>The services of the startup tenants are built once, as before. Each runtime change builds a
 * registry holding one tenant's services, and the registry the APIs see routes every per-tenant
 * accessor to it; cluster-wide accessors, which fold over the startup tenants, stay as they were.
 * Building a tenant's services needs its secondary storage to be reachable through the node's
 * search clients, which holds for a node without secondary storage; for any other node a change
 * whose services cannot be built keeps the tenant's previous services, if any.
 */
@NullMarked
public final class PhysicalTenantServiceRegistry implements PhysicalTenantResources {

  private static final Logger LOG = LoggerFactory.getLogger(PhysicalTenantServiceRegistry.class);

  private final ServiceRegistry startup;
  private final PhysicalTenantServiceFactory factory;
  private final Map<String, ServiceRegistry> runtime = new ConcurrentHashMap<>();
  private final ServiceRegistry live;

  PhysicalTenantServiceRegistry(
      final ServiceRegistry startup, final PhysicalTenantServiceFactory factory) {
    this.startup = startup;
    this.factory = factory;
    live =
        (ServiceRegistry)
            Proxy.newProxyInstance(
                ServiceRegistry.class.getClassLoader(),
                new Class<?>[] {ServiceRegistry.class},
                new Routing());
  }

  /** The registry the APIs use. */
  public ServiceRegistry registry() {
    return live;
  }

  /** The tenants whose services were built at runtime. */
  public Set<String> runtimeTenants() {
    return Set.copyOf(runtime.keySet());
  }

  @Override
  public void apply(final String physicalTenantId, final Camunda configuration) {
    final var builder = new DefaultServiceRegistry.Builder();
    try {
      factory.register(
          builder,
          physicalTenantId,
          configuration,
          new HashMap<>(),
          new HashMap<>(),
          new ArrayList<>());
    } catch (final RuntimeException e) {
      LOG.warn(
          "Cannot build the REST services of physical tenant '{}'; it keeps its previous services,"
              + " if any",
          physicalTenantId,
          e);
      return;
    }
    runtime.put(physicalTenantId, builder.build());
  }

  @Override
  public void release(final String physicalTenantId) {
    runtime.remove(physicalTenantId);
  }

  private final class Routing implements InvocationHandler {

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args)
        throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        return switch (method.getName()) {
          case "equals" -> proxy == args[0];
          case "hashCode" -> System.identityHashCode(proxy);
          default -> "PhysicalTenantServiceRegistry" + runtime.keySet();
        };
      }
      final ServiceRegistry target =
          args != null && args.length == 1 && args[0] instanceof final String physicalTenantId
              ? runtime.getOrDefault(physicalTenantId, startup)
              : startup;
      try {
        return method.invoke(target, args);
      } catch (final InvocationTargetException e) {
        throw e.getCause();
      }
    }
  }
}
