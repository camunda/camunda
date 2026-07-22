/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.secretstore.NoopSecretStore;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.aws.AwsSecretsManagerSecretStore;
import io.camunda.secretstore.aws.AwsSecretsManagerStoreConfig;
import io.camunda.secretstore.file.FileBasedSecretStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers one {@link SecretStoreRegistry} per physical tenant, keyed by physical tenant ID. */
@Configuration(proxyBeanMethods = false)
@NullMarked
public class SecretStoreConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(SecretStoreConfiguration.class);
  private static final NoopSecretStore NOOP_STORE = new NoopSecretStore();

  @Bean
  public Map<String, SecretStoreRegistry> secretStoreRegistries(
      final PhysicalTenantResolver resolver) {
    final Map<String, SecretStoreRegistry> registries = new LinkedHashMap<>();
    // tracks every store successfully constructed across all tenants processed so far, so a
    // failure partway through (e.g. a later tenant's AWS store failing to build) can close
    // them instead of leaking their underlying clients/connections
    final List<SecretStore> created = new ArrayList<>();
    try {
      resolver
          .mapValues(Camunda::getSecrets)
          .forEach(
              (tenantId, secrets) -> {
                final var fileStores = secrets.getStores().getFile();
                final var awsStores = secrets.getStores().getAws();
                // cap is one store total per tenant, counted across all store types combined
                final var totalStores = fileStores.size() + awsStores.size();
                if (totalStores > 1) {
                  throw new IllegalStateException(
                      "Physical tenant '"
                          + tenantId
                          + "' has "
                          + totalStores
                          + " secret stores configured, but only one is supported at this time");
                }
                final Map<String, SecretStore> stores = new LinkedHashMap<>();
                fileStores.forEach(
                    (storeId, fileStore) -> {
                      final var path = fileStore.getPath();
                      if (path.isBlank()) {
                        throw new IllegalStateException(
                            "File store '"
                                + storeId
                                + "' for physical tenant '"
                                + tenantId
                                + "' has no path configured");
                      }
                      registerStore(
                          stores,
                          created,
                          storeId,
                          tenantId,
                          "file",
                          new FileBasedSecretStore(Path.of(path)));
                    });
                awsStores.forEach(
                    (storeId, awsStore) -> {
                      final var config =
                          new AwsSecretsManagerStoreConfig(
                              awsStore.getRegion(),
                              awsStore.getPathPrefix(),
                              awsStore.getContainerSecretId(),
                              null,
                              AwsSecretsManagerStoreConfig.DEFAULT_MAX_RETRIES,
                              awsStore.isBatchEnabled(),
                              awsStore.getBatchSize());
                      registerStore(
                          stores,
                          created,
                          storeId,
                          tenantId,
                          "AWS Secrets Manager",
                          AwsSecretsManagerSecretStore.fromConfig(config));
                    });
                if (stores.isEmpty()) {
                  stores.put("default", NOOP_STORE);
                  LOG.info(
                      "No secret stores configured for physical tenant '{}', using noop store",
                      tenantId);
                }
                registries.put(tenantId, new SecretStoreRegistry(Map.copyOf(stores)));
              });
    } catch (final RuntimeException e) {
      closeAll(created);
      throw e;
    }
    return Map.copyOf(registries);
  }

  private static void closeAll(final List<SecretStore> stores) {
    for (final var store : stores) {
      try {
        store.close();
      } catch (final RuntimeException closeFailure) {
        LOG.warn("Failed to close secret store while rolling back a failed startup", closeFailure);
      }
    }
  }

  private static void registerStore(
      final Map<String, SecretStore> stores,
      final List<SecretStore> created,
      final String storeId,
      final String tenantId,
      final String storeType,
      final SecretStore store) {
    final var normalizedId = storeId.trim().toLowerCase();
    stores.put(normalizedId, store);
    created.add(store);
    LOG.info(
        "Registered {} secret store '{}' for physical tenant '{}'",
        storeType,
        normalizedId,
        tenantId);
  }
}
