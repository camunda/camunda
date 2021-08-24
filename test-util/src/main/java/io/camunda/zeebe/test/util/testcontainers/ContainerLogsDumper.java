/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.testcontainers;

import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;

/** A utility class to dump the logs of a set of containers. */
@SuppressWarnings("java:S1452")
public final class ContainerLogsDumper implements AfterTestExecutionCallback {
  private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(ContainerLogsDumper.class);
  private static final Pattern DOUBLE_NEWLINE = Pattern.compile("\n\n");

  private final Supplier<Map<?, ? extends Container<?>>> containersSupplier;
  private final Logger logger;

  @SuppressWarnings("unused")
  public ContainerLogsDumper(final Supplier<Map<?, ? extends Container<?>>> containersSupplier) {
    this(containersSupplier, DEFAULT_LOGGER);
  }

  public ContainerLogsDumper(
      final Supplier<Map<?, ? extends Container<?>>> containers, final Logger logger) {
    this.containersSupplier = containers;
    this.logger = logger;
  }

  @Override
  public void afterTestExecution(final ExtensionContext context) {
    final var testFailed = context.getExecutionException().isPresent();
    if (!testFailed) {
      return;
    }

    final var containers = containersSupplier.get();
    for (final var container : containers.entrySet()) {
      final var id = container.getKey();

      try {
        dumpContainerLogs(id, container.getValue());
      } catch (final Exception e) {
        logger.warn("Failed to extract logs from container {}", id, e);
      }
    }
  }

  private void dumpContainerLogs(final Object id, final Container<?> container) {
    if (!container.isRunning()) {
      logger.info("No logs to dump for stopped container {}", id);
      return;
    }

    if (logger.isErrorEnabled()) {
      final var logs = container.getLogs().replaceAll(DOUBLE_NEWLINE.pattern(), "\n");
      logger.error(
          "{}==============================================={}{} logs{}==============================================={}{}",
          System.lineSeparator(),
          System.lineSeparator(),
          id,
          System.lineSeparator(),
          System.lineSeparator(),
          logs);
    }
  }
}
