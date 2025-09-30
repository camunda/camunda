/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ExporterEntity;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.Extension;

public interface DatabaseTestExtension extends Extension {

  void assertMaxOpenScrollContexts(final int maxOpenScrollContexts);

  void refreshTasklistIndices();

  default void waitFor(
      final Duration timeout, final TestCheck testCheck, final Object... arguments) {
    Awaitility.await()
        .atMost(timeout)
        .pollInterval(Duration.ofMillis(500))
        .until(
            () -> {
              final boolean found = testCheck.test(arguments);
              if (!found) {
                refreshTasklistIndices();
              }
              return found;
            });
  }

  default void waitFor(final TestCheck testCheck, final Object... arguments) {
    waitFor(Duration.ofSeconds(30), testCheck, arguments);
  }

  int getOpenScrollcontextSize();

  <T extends ExporterEntity> void bulkIndex(
      final IndexDescriptor index,
      final List<T> documents,
      final Function<T, String> routingFunction)
      throws IOException;

  default <T extends ExporterEntity> void bulkIndex(
      final IndexDescriptor index, final List<T> documents) throws IOException {
    bulkIndex(index, documents, ExporterEntity::getId);
  }
}
