/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.ConfigurationSecretReference;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;

/**
 * Resolves the secret references in the configuration of every physical tenant, and writes their
 * values in place.
 *
 * <p>When the environment holds a reference, this happens as soon as the root {@link Camunda} is
 * bound, so that no other bean can read a reference in place of its value.
 */
public final class ConfigurationSecrets implements BeanPostProcessor, BeanFactoryAware {

  private final Environment environment;
  private @Nullable ConfigurationSecretStores stores;
  private final ConfigurationReferences references = new ConfigurationReferences();
  private @Nullable PhysicalTenantResolver resolver;
  private @Nullable BeanFactory beanFactory;

  public ConfigurationSecrets(final Environment environment) {
    this.environment = environment;
  }

  @Override
  public void setBeanFactory(final BeanFactory beanFactory) {
    this.beanFactory = beanFactory;
  }

  @Override
  public Object postProcessAfterInitialization(final Object bean, final String beanName) {
    if (!holdsReference(environment)) {
      return bean;
    }
    if (bean instanceof final Camunda camunda) {
      resolve(camunda);
    } else if (bean instanceof final CamundaSecurityLibraryProperties security) {
      resolveSecurityLibrary(security);
    }
    return bean;
  }

  /**
   * Resolves the configuration of every physical tenant from the given root configuration, unless
   * this already happened, and returns the resolver that holds it.
   */
  public synchronized PhysicalTenantResolver resolve(final Camunda camunda) {
    if (resolver != null) {
      return resolver;
    }
    final var resolved = PhysicalTenantResolver.of(environment, camunda);
    final var stores = ConfigurationSecretStores.of(resolved);
    try {
      resolved
          .getAll()
          .forEach(
              (tenantId, configuration) ->
                  references.put(
                      tenantId,
                      ConfigurationSecretResolver.resolveInPlace(
                          tenantId, configuration, stores.forPhysicalTenant(tenantId))));
      final var defaultTenantId = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
      if (resolved.forPhysicalTenant(defaultTenantId) != camunda) {
        ConfigurationSecretResolver.resolveInPlace(
            defaultTenantId, camunda, stores.forPhysicalTenant(defaultTenantId));
      }
    } catch (final Throwable e) {
      try {
        stores.close();
      } catch (final Throwable closeFailure) {
        e.addSuppressed(closeFailure);
      }
      throw e;
    }
    this.stores = stores;
    resolver = resolved;
    return resolved;
  }

  /** The stores that {@link #resolve} read from, once it has. */
  public ConfigurationSecretStores stores() {
    return Objects.requireNonNull(stores, "The configuration hasn't been resolved yet");
  }

  public ConfigurationReferences references() {
    return references;
  }

  /**
   * The security library binds {@code camunda.security.*} itself, for the cluster as a whole, so
   * its copy of the root configuration is resolved with the store of {@code default} too.
   */
  private void resolveSecurityLibrary(final CamundaSecurityLibraryProperties security) {
    final var camunda =
        beanFactory == null ? null : beanFactory.getBeanProvider(Camunda.class).getIfAvailable();
    if (camunda == null) {
      return;
    }
    resolve(camunda);
    final var defaultTenantId = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
    ConfigurationSecretResolver.resolveInPlace(
        defaultTenantId, security, "security.", stores().forPhysicalTenant(defaultTenantId));
  }

  private static boolean holdsReference(final Environment environment) {
    return environment instanceof final ConfigurableEnvironment configurable
        && configurable.getPropertySources().stream()
            .filter(EnumerablePropertySource.class::isInstance)
            .map(EnumerablePropertySource.class::cast)
            .anyMatch(ConfigurationSecrets::holdsReference);
  }

  private static boolean holdsReference(final EnumerablePropertySource<?> source) {
    for (final var name : source.getPropertyNames()) {
      if (source.getProperty(name) instanceof final CharSequence value
          && ConfigurationSecretReference.isReference(value.toString())) {
        return true;
      }
    }
    return false;
  }
}
