/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.app;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.Migrator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RuntimeContainer {

  private static final Logger LOG = LoggerFactory.getLogger(RuntimeContainer.class);
  private final List<Migrator> migrators;
  private final ThreadPoolExecutor executor;

  public RuntimeContainer(final List<Migrator> migrators) {
    this.migrators = migrators;
    executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(migrators.size());
  }

  public void start() throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(migrators.size());
    migrators.forEach(
        m ->
            executor.submit(
                () -> {
                  try {
                    m.run();
                  } catch (final MigrationException ex) {
                    LOG.error(ex.getMessage());
                  } finally {
                    latch.countDown();
                  }
                }));
    latch.await();
  }
}
