/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineIndexPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineRetentionPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineSchemaManagerPropertiesOverride;
import io.camunda.configuration.beans.LegacySearchEngineConnectProperties;
import io.camunda.configuration.beans.LegacySearchEngineIndexProperties;
import io.camunda.configuration.beans.LegacySearchEngineRetentionProperties;
import io.camunda.configuration.beans.LegacySearchEngineSchemaManagerProperties;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SearchEngineSchemaInitializerTest {

  @BeforeAll
  @AfterAll
  static void clearStaticEnvironment() {
    // Avoid leaking a previous test's environment into these unit tests.
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  /** Builds an initializer with a single synthesized {@code default} tenant for an ES backend. */
  private SearchEngineSchemaInitializer createInitializer() {
    final Camunda camunda = new Camunda();
    camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);
    camunda.getData().getSecondaryStorage().getElasticsearch().setUrl("http://localhost:9200");
    final PhysicalTenantResolver resolver =
        PhysicalTenantResolver.of(new MockEnvironment(), camunda);
    return new SearchEngineSchemaInitializer(configsFor(resolver), new SimpleMeterRegistry(), true);
  }

  private static Map<String, SearchEngineConfiguration> configsFor(
      final PhysicalTenantResolver resolver) {
    final UnifiedConfiguration unifiedConfig = new UnifiedConfiguration();
    final SearchEngineConnectPropertiesOverride connectOverride =
        new SearchEngineConnectPropertiesOverride(
            unifiedConfig, new LegacySearchEngineConnectProperties());
    final SearchEngineIndexPropertiesOverride indexOverride =
        new SearchEngineIndexPropertiesOverride(
            unifiedConfig, new LegacySearchEngineIndexProperties());
    final SearchEngineRetentionPropertiesOverride retentionOverride =
        new SearchEngineRetentionPropertiesOverride(
            unifiedConfig, new LegacySearchEngineRetentionProperties());
    final SearchEngineSchemaManagerPropertiesOverride schemaManagerOverride =
        new SearchEngineSchemaManagerPropertiesOverride(
            unifiedConfig, new LegacySearchEngineSchemaManagerProperties());
    return resolver.mapValues(
        tenantCamunda ->
            SearchEngineConfiguration.of(
                b ->
                    b.connect(connectOverride.searchEngineConnectProperties(tenantCamunda))
                        .index(indexOverride.searchEngineIndexProperties(tenantCamunda))
                        .retention(retentionOverride.searchEngineRetentionProperties(tenantCamunda))
                        .schemaManager(
                            schemaManagerOverride.searchEngineSchemaManagerProperties(
                                tenantCamunda))));
  }

  /** Sets the private {@code isShutdown} flag on the initializer via reflection. */
  private static void setShutdown(
      final SearchEngineSchemaInitializer initializer, final boolean value) {
    try {
      final Field field = SearchEngineSchemaInitializer.class.getDeclaredField("isShutdown");
      field.setAccessible(true);
      ((AtomicBoolean) field.get(initializer)).set(value);
    } catch (final ReflectiveOperationException e) {
      throw new RuntimeException("Failed to set isShutdown via reflection", e);
    }
  }

  @Nested
  class SynchronousInitialization {

    private ExecutorService executor;

    @AfterEach
    void tearDown() {
      if (executor != null && !executor.isShutdown()) {
        executor.shutdownNow();
      }
      // Clear interrupt flag to avoid leaking to other tests
      Thread.interrupted();
    }

    @Test
    void shouldCompleteSuccessfully() throws Exception {
      // given
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();
      final var future = CompletableFuture.completedFuture((Void) null);

      // when
      initializer.synchronousSchemaInitialization(future, executor);

      // then — no exception thrown, executor is closed
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldNotInterruptThreadOnShutdownTriggeredInterrupt() throws Exception {
      // given — simulate shutdown-triggered interrupt
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();
      setShutdown(initializer, true);

      // Use an incomplete future so that future.get() blocks and sees the interrupt flag
      final var blockingFuture = new CompletableFuture<Void>();

      // when — interrupt the thread so future.get() throws InterruptedException
      Thread.currentThread().interrupt();
      initializer.synchronousSchemaInitialization(blockingFuture, executor);

      // then — thread should NOT be interrupted (shutdown swallows the interrupt)
      assertThat(Thread.currentThread().isInterrupted()).isFalse();
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldReInterruptThreadOnNonShutdownInterrupt() throws Exception {
      // given — NOT a shutdown, something else interrupted
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();
      // isShutdown remains false (default)

      final var blockingFuture = new CompletableFuture<Void>();

      // when — interrupt the current thread so future.get() throws InterruptedException
      Thread.currentThread().interrupt();
      initializer.synchronousSchemaInitialization(blockingFuture, executor);

      // then — thread should be re-interrupted
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldSuppressExceptionDuringShutdown() {
      // given — shutdown is in progress and the future fails with a non-interrupt exception.
      // CompletableFuture.get() wraps the cause in ExecutionException, which falls into
      // the catch(Exception) block where isShutdown=true causes a silent return.
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();
      setShutdown(initializer, true);

      final var future = new CompletableFuture<Void>();
      future.completeExceptionally(new RuntimeException("ES connection failed"));

      // when/then — should not throw, exception is suppressed during shutdown
      assertThatNoException()
          .isThrownBy(() -> initializer.synchronousSchemaInitialization(future, executor));
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldPropagateExceptionWhenNotShutdown() {
      // given — NOT a shutdown, the future fails with a real error.
      // CompletableFuture.get() wraps the cause in ExecutionException.
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();
      // isShutdown remains false

      final var cause = new RuntimeException("ES connection refused");
      final var future = new CompletableFuture<Void>();
      future.completeExceptionally(cause);

      // when/then — ExecutionException wrapping the cause should propagate
      assertThatThrownBy(() -> initializer.synchronousSchemaInitialization(future, executor))
          .isInstanceOf(ExecutionException.class)
          .hasCause(cause);
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldAlwaysCloseExecutorEvenOnException() {
      // given
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();

      final var future = new CompletableFuture<Void>();
      future.completeExceptionally(new RuntimeException("schema init failed"));

      // when
      try {
        initializer.synchronousSchemaInitialization(future, executor);
      } catch (final Exception ignored) {
        // expected
      }

      // then — executor must be closed regardless
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldAlwaysCloseExecutorOnInterrupt() throws Exception {
      // given
      final var initializer = createInitializer();
      executor = Executors.newSingleThreadExecutor();

      final var blockingFuture = new CompletableFuture<Void>();
      Thread.currentThread().interrupt();

      // when
      initializer.synchronousSchemaInitialization(blockingFuture, executor);

      // then — executor must be closed regardless
      assertThat(executor.isShutdown()).isTrue();
    }
  }

  @Nested
  class AsynchronousInitialization {

    @Test
    void shouldCloseExecutorOnSuccess() throws Exception {
      // given
      final var initializer = createInitializer();
      final var executor = Executors.newSingleThreadExecutor();
      final var future = CompletableFuture.completedFuture((Void) null);

      // when
      initializer.asyncSchemaInitialization(future, executor);

      // then — wait a bit for the whenCompleteAsync callback to run
      executor.awaitTermination(2, TimeUnit.SECONDS);
      assertThat(executor.isShutdown()).isTrue();
    }

    @Test
    void shouldCloseExecutorOnFailure() throws Exception {
      // given
      final var initializer = createInitializer();
      final var executor = Executors.newSingleThreadExecutor();
      final var future = new CompletableFuture<Void>();
      future.completeExceptionally(new RuntimeException("ES unavailable"));

      // when
      initializer.asyncSchemaInitialization(future, executor);

      // then — wait a bit for the whenCompleteAsync callback to run
      executor.awaitTermination(2, TimeUnit.SECONDS);
      assertThat(executor.isShutdown()).isTrue();
    }
  }

  @Nested
  class PerTenantResolution {

    @Test
    void shouldSynthesizeDefaultTenantWhenNoneDeclared() {
      // given
      final Camunda camunda = new Camunda();
      camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);
      camunda.getData().getSecondaryStorage().getElasticsearch().setIndexPrefix("default-prefix");
      final PhysicalTenantResolver resolver =
          PhysicalTenantResolver.of(new MockEnvironment(), camunda);

      // when
      final SearchEngineSchemaInitializer initializer =
          new SearchEngineSchemaInitializer(configsFor(resolver), new SimpleMeterRegistry(), true);

      // then
      assertThat(initializer.isInitialized("default")).isFalse();
      assertThat(initializer.isInitialized()).isFalse();
      assertThat(initializer.forTenant("default").indices()).isNotEmpty();
    }

    @Test
    void shouldThrowForUnknownTenant() {
      // given
      final SearchEngineSchemaInitializer initializer = createInitializer();

      // when/then
      assertThatThrownBy(() -> initializer.forTenant("unknown"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("unknown");
    }

    @Test
    void shouldReportPerTenantReadiness() throws Exception {
      // given
      final SearchEngineSchemaInitializer initializer = createInitializer();

      // initially neither no-arg nor per-tenant readiness is true
      assertThat(initializer.isInitialized()).isFalse();
      assertThat(initializer.isInitialized("default")).isFalse();

      // when — flip the per-tenant flag via reflection (simulating a successful init)
      final Field field = SearchEngineSchemaInitializer.class.getDeclaredField("initialized");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      final java.util.Map<String, AtomicBoolean> map =
          (java.util.Map<String, AtomicBoolean>) field.get(initializer);
      map.get("default").set(true);

      // then
      assertThat(initializer.isInitialized("default")).isTrue();
      assertThat(initializer.isInitialized()).isTrue();
    }

    @Test
    void shouldReturnFalseForNoArgWhenNotAllTenantsReady() throws Exception {
      // given — two explicitly declared tenants (default + tenantb), only one ready.
      // We declare `default` explicitly to suppress the resolver's synthesized default entry,
      // which would otherwise leave a third tenant permanently un-ready.
      final MockEnvironment env = new MockEnvironment();
      env.getPropertySources()
          .addFirst(
              new org.springframework.core.env.MapPropertySource(
                  "test",
                  java.util.Map.of(
                      "camunda.physical-tenants.default.data.secondary-storage.type",
                      "elasticsearch",
                      "camunda.physical-tenants.tenantb.data.secondary-storage.type",
                      "elasticsearch")));
      final Camunda camunda = new Camunda();
      camunda.getData().getSecondaryStorage().setType(SecondaryStorageType.elasticsearch);
      camunda.getData().getSecondaryStorage().getElasticsearch().setUrl("http://es:9200");
      final PhysicalTenantResolver resolver = PhysicalTenantResolver.of(env, camunda);
      final SearchEngineSchemaInitializer initializer =
          new SearchEngineSchemaInitializer(configsFor(resolver), new SimpleMeterRegistry(), true);

      // when — flip readiness on only one tenant
      final Field field = SearchEngineSchemaInitializer.class.getDeclaredField("initialized");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      final java.util.Map<String, AtomicBoolean> map =
          (java.util.Map<String, AtomicBoolean>) field.get(initializer);
      assertThat(map).containsOnlyKeys("default", "tenantb");
      map.get("default").set(true);

      // then
      assertThat(initializer.isInitialized("default")).isTrue();
      assertThat(initializer.isInitialized("tenantb")).isFalse();
      assertThat(initializer.isInitialized()).isFalse();

      // when — flip the second tenant
      map.get("tenantb").set(true);

      // then
      assertThat(initializer.isInitialized()).isTrue();
    }
  }
}
