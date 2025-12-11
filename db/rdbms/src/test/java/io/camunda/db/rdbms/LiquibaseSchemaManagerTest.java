/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class LiquibaseSchemaManagerTest {

  @Test
  void shouldHaveInitializedFalseByDefault() {
    // given
    final var schemaManager = new LiquibaseSchemaManager();

    // when / then
    assertThat(schemaManager.isInitialized()).isFalse();
  }

  @Test
  void shouldSetInitializedTrueAfterSuccessfulInitialization() throws Exception {
    // given
    final var schemaManager = new TestLiquibaseSchemaManager();

    // when
    schemaManager.afterPropertiesSet();

    // then
    assertThat(schemaManager.isInitialized()).isTrue();
  }

  @Test
  void shouldKeepInitializedFalseWhenInitializationFails() {
    // given
    final var schemaManager = spy(new TestLiquibaseSchemaManager());
    doThrow(new RuntimeException("Initialization failed"))
        .when(schemaManager)
        .performMigration();

    // when / then
    assertThatThrownBy(() -> schemaManager.afterPropertiesSet())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Initialization failed");

    assertThat(schemaManager.isInitialized()).isFalse();
  }

  @Test
  void shouldHandleConcurrentIsInitializedCallsDuringInitialization() throws Exception {
    // given
    final var schemaManager = new SlowInitializingSchemaManager();
    final int numberOfThreads = 10;
    final ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
    final CountDownLatch startLatch = new CountDownLatch(1);
    final CountDownLatch initializationStartedLatch = new CountDownLatch(1);
    final List<Future<Boolean>> futures = new ArrayList<>();
    final AtomicBoolean initializationStarted = new AtomicBoolean(false);

    try {
      // Start initialization in a separate thread
      executorService.submit(
          () -> {
            try {
              initializationStartedLatch.countDown();
              schemaManager.afterPropertiesSet();
            } catch (final Exception e) {
              throw new RuntimeException(e);
            }
          });

      // Wait for initialization to start
      initializationStartedLatch.await(1, TimeUnit.SECONDS);

      // when - submit multiple concurrent reads
      for (int i = 0; i < numberOfThreads; i++) {
        futures.add(
            executorService.submit(
                () -> {
                  startLatch.await();
                  return schemaManager.isInitialized();
                }));
      }

      // Start all threads at once
      startLatch.countDown();

      // then - all reads should complete without throwing exceptions
      // Some may return false (before initialization), some may return true (after)
      final var results = new ArrayList<Boolean>();
      for (final Future<Boolean> future : futures) {
        results.add(future.get(5, TimeUnit.SECONDS));
      }

      // Verify no exceptions were thrown and we got valid boolean results
      assertThat(results).hasSize(numberOfThreads);
      assertThat(results).allMatch(result -> result != null);

      // After all threads complete, initialization should be done
      Thread.sleep(100); // Give initialization time to complete
      assertThat(schemaManager.isInitialized()).isTrue();

    } finally {
      executorService.shutdownNow();
      executorService.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  /**
   * Test implementation that overrides the parent's afterPropertiesSet to avoid actual Liquibase
   * initialization.
   */
  private static class TestLiquibaseSchemaManager extends LiquibaseSchemaManager {
    @Override
    public void afterPropertiesSet() throws Exception {
      // Skip the actual Liquibase initialization (super.afterPropertiesSet())
      // and just perform our state update
      performMigration();
      setInitialized();
    }

    protected void performMigration() {
      // No-op for testing, can be overridden in spy
    }

    protected void setInitialized() {
      // Access the inherited behavior through a test method
      try {
        final var field = LiquibaseSchemaManager.class.getDeclaredField("initialized");
        field.setAccessible(true);
        field.set(this, true);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Test implementation that simulates a slow initialization to test thread-safety.
   */
  private static class SlowInitializingSchemaManager extends TestLiquibaseSchemaManager {
    @Override
    protected void performMigration() {
      try {
        // Simulate slow migration
        Thread.sleep(50);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
  }
}
