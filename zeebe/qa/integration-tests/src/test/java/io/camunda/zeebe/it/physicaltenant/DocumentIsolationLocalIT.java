/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

@ZeebeIntegration
final class DocumentIsolationLocalIT extends AbstractDocumentIsolationIT {

  private static final Path STORE_DIR_A = createTempDir("doc-store-a-");
  private static final Path STORE_DIR_B = createTempDir("doc-store-b-");

  @SuppressWarnings("resource") // lifecycle managed by @TestZeebe
  @TestZeebe(purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              .withProperty("camunda.document.local.store-a.path", STORE_DIR_A.toString())
              .withProperty("camunda.document.local.store-b.path", STORE_DIR_B.toString())
              .withProperty("camunda.document.default-store-id", STORE_A)
              .withProperty("camunda.physical-tenants.tenanta.document.assigned[0]", STORE_A)
              .withProperty("camunda.physical-tenants.tenantb.document.assigned[0]", STORE_B)
              .withProperty("camunda.physical-tenants.tenantb.document.default-store-id", STORE_B));

  @BeforeAll
  static void setUp() {
    clearDirectoryContents(STORE_DIR_A);
    clearDirectoryContents(STORE_DIR_B);
    startClients(BROKER);
  }

  @AfterAll
  static void tearDown() {
    closeClients();
    deleteDirectory(STORE_DIR_A);
    deleteDirectory(STORE_DIR_B);
  }

  private static Path createTempDir(final String prefix) {
    try {
      return Files.createTempDirectory(prefix);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to create temp dir for document store", e);
    }
  }

  private static void clearDirectoryContents(final Path dir) {
    if (dir == null || !Files.exists(dir)) {
      return;
    }
    try (final var paths = Files.walk(dir)) {
      paths
          .filter(p -> !p.equals(dir))
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (final IOException ignored) {
                  // do nothing
                }
              });
    } catch (final IOException ignored) {
      // do nothing
    }
  }

  private static void deleteDirectory(final Path dir) {
    clearDirectoryContents(dir);
    if (dir != null) {
      try {
        Files.deleteIfExists(dir);
      } catch (final IOException ignored) {
        // do nothing
      }
    }
  }
}
