/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Secrets.FileStore;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;

/** Backs each physical tenant's secret store with a directory of its own, one file per secret. */
final class PhysicalTenantFileSecretIsolationIT extends AbstractPhysicalTenantSecretIsolationIT {

  private static final Map<String, Path> STORE_BY_TENANT =
      SECRETS_BY_TENANT.keySet().stream()
          .collect(
              Collectors.toUnmodifiableMap(
                  Function.identity(), PhysicalTenantFileSecretIsolationIT::createStore));

  @AfterAll
  static void deleteStores() {
    STORE_BY_TENANT.values().forEach(PhysicalTenantFileSecretIsolationIT::deleteQuietly);
  }

  @Override
  protected void configureStore(final String tenantId, final Camunda tenantConfig) {
    final var store = new FileStore();
    store.setPath(STORE_BY_TENANT.get(tenantId).toString());
    tenantConfig.getSecrets().getStores().getFile().put("default", store);
  }

  private static Path createStore(final String tenantId) {
    try {
      final Path directory = Files.createTempDirectory("secret-store-" + tenantId + "-");
      for (final var secret : SECRETS_BY_TENANT.get(tenantId).entrySet()) {
        Files.writeString(
            directory.resolve(secret.getKey()), secret.getValue(), StandardCharsets.UTF_8);
      }
      return directory;
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to write the store of tenant " + tenantId, e);
    }
  }

  private static void deleteQuietly(final Path directory) {
    try {
      FileUtil.deleteFolderIfExists(directory);
    } catch (final IOException e) {
      // a leftover temp directory is not worth failing a passing test over
    }
  }
}
