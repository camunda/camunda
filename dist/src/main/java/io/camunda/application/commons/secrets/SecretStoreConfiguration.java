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
import java.util.LinkedHashMap;
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
    resolver
        .mapValues(Camunda::getSecrets)
        .forEach(
            (tenantId, secrets) -> {
              final var fileStores = secrets.getStores().getFile();
              final var awsStores = secrets.getStores().getAwsSecretsManager();
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
                        stores, storeId, tenantId, "file", new FileBasedSecretStore(Path.of(path)));
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
    return Map.copyOf(registries);
  }

  private static void registerStore(
      final Map<String, SecretStore> stores,
      final String storeId,
      final String tenantId,
      final String storeType,
      final SecretStore store) {
    final var normalizedId = storeId.trim().toLowerCase();
    stores.put(normalizedId, store);
    LOG.info(
        "Registered {} secret store '{}' for physical tenant '{}'",
        storeType,
        normalizedId,
        tenantId);
  }
}
