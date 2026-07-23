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
import io.camunda.secretstore.SecretStoreRegistry;
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
  public SecretStoreRegistries secretStoreRegistries(final PhysicalTenantResolver resolver) {
    final Map<String, SecretStoreRegistry> registries = new LinkedHashMap<>();
    resolver
        .mapValues(Camunda::getSecrets)
        .forEach(
            (tenantId, secrets) -> {
              final var fileStores = secrets.getStores().getFile();
              if (fileStores.size() > 1) {
                throw new IllegalStateException(
                    "Physical tenant '"
                        + tenantId
                        + "' has "
                        + fileStores.size()
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
                    stores.put(
                        storeId.trim().toLowerCase(), new FileBasedSecretStore(Path.of(path)));
                    LOG.info(
                        "Registered file secret store '{}' for physical tenant '{}'",
                        storeId.trim().toLowerCase(),
                        tenantId);
                  });
              if (stores.isEmpty()) {
                stores.put("default", NOOP_STORE);
                LOG.info(
                    "No secret stores configured for physical tenant '{}', using noop store",
                    tenantId);
              }
              registries.put(tenantId, new SecretStoreRegistry(Map.copyOf(stores)));
            });
    return new SecretStoreRegistries(Map.copyOf(registries));
  }
}
