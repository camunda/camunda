/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

/**
 * Refuses the environment-variable secret store when the cluster runs in SaaS, for the cluster or
 * any physical tenant. SaaS tenants share one process environment that the platform, not the
 * tenant, controls, so SaaS stays on the file store.
 *
 * <p>The cluster counts as SaaS if any tenant resolves {@code camunda.security.saas.cluster-id},
 * since that property can be overridden per tenant.
 */
@NullMarked
class EnvSecretStoreSaasValidation implements CrossTenantValidation {

  @Override
  public void validate(final Map<String, Camunda> resolvedByTenant) {
    final List<String> saasTenants =
        tenantsMatching(resolvedByTenant, EnvSecretStoreSaasValidation::isSaas);
    if (saasTenants.isEmpty()) {
      return;
    }

    final List<String> envStoreTenants =
        tenantsMatching(
            resolvedByTenant, camunda -> !camunda.getSecrets().getStores().getEnv().isEmpty());
    if (!envStoreTenants.isEmpty()) {
      throw new UnifiedConfigurationException(
          ("The environment-variable secret store is not supported in SaaS: remove "
                  + "camunda.secrets.stores.env and camunda.physical-tenants.<id>.secrets.stores.env "
                  + "and use the file store instead. Configured for physical tenants %s; SaaS is "
                  + "set by camunda.security.saas.cluster-id for physical tenants %s.")
              .formatted(envStoreTenants, saasTenants));
    }
  }

  private static boolean isSaas(final Camunda camunda) {
    final String clusterId = camunda.getSecurity().getSaas().getClusterId();
    return clusterId != null && !clusterId.isBlank();
  }

  private static List<String> tenantsMatching(
      final Map<String, Camunda> resolvedByTenant, final Predicate<Camunda> predicate) {
    return resolvedByTenant.entrySet().stream()
        .filter(tenant -> predicate.test(tenant.getValue()))
        .map(Entry::getKey)
        .sorted()
        .toList();
  }
}
