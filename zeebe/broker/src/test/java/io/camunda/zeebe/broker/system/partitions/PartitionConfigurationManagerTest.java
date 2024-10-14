/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.broker.exporter.stream.ExporterDirector.ExporterInitializationInfo;
import io.camunda.zeebe.broker.exporter.util.TestExporterFactory;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.jar.ExternalJarRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PartitionConfigurationManagerTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(PartitionConfigurationManagerTest.class);
  private final TestConcurrencyControl testConcurrencyControl = new TestConcurrencyControl();
  private TestPartitionTransitionContext partitionTransitionContext;
  private PartitionConfigurationManager partitionConfigurationManager;

  @Nested
  final class ExporterDisable {
    private final String exporterId = "exporterA";
    private final DynamicPartitionConfig partitionConfig =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of(exporterId, new ExporterState(0, State.ENABLED, Optional.empty()))));

    @BeforeEach
    void setup() {
      partitionTransitionContext = new TestPartitionTransitionContext();
      partitionTransitionContext.setExporterRepository(new ExporterRepository());
      partitionConfigurationManager =
          new PartitionConfigurationManager(
              LOGGER,
              partitionTransitionContext,
              partitionTransitionContext.getExportedDescriptors(),
              testConcurrencyControl);
    }

    @Test
    void shouldDisableExporterAndUpdateConfigInContext() {
      // given
      partitionTransitionContext.setDynamicPartitionConfig(partitionConfig);
      final ExporterDirector mockExporterDirector = mock(ExporterDirector.class);
      when(mockExporterDirector.disableExporter(exporterId))
          .thenReturn(testConcurrencyControl.createCompletedFuture());
      partitionTransitionContext.setExporterDirector(mockExporterDirector);

      // when
      partitionConfigurationManager.disableExporter(exporterId).join();

      // then
      assertThat(
              partitionTransitionContext
                  .getDynamicPartitionConfig()
                  .exporting()
                  .exporters()
                  .get(exporterId)
                  .state())
          .describedAs("Exporter state should be updated in the context")
          .isEqualTo(State.DISABLED);
      verify(mockExporterDirector).disableExporter(exporterId);
    }

    @Test
    void shouldUpdateConfigInContextWhenExporterDirectorIsNotAvailable() {
      // given
      partitionTransitionContext.setDynamicPartitionConfig(partitionConfig);

      // when
      partitionConfigurationManager.disableExporter(exporterId).join();

      // then
      assertThat(
              partitionTransitionContext
                  .getDynamicPartitionConfig()
                  .exporting()
                  .exporters()
                  .get(exporterId)
                  .state())
          .describedAs("Exporter state should be updated in the context")
          .isEqualTo(State.DISABLED);
    }

    @Test
    void shouldFailFutureIfDisablingExporterFailed() {
      // given
      partitionTransitionContext.setDynamicPartitionConfig(partitionConfig);
      final ExporterDirector mockExporterDirector = mock(ExporterDirector.class);
      when(mockExporterDirector.disableExporter(exporterId))
          .thenReturn(testConcurrencyControl.failedFuture(new RuntimeException("force fail")));
      partitionTransitionContext.setExporterDirector(mockExporterDirector);

      // when - then
      assertThat(partitionConfigurationManager.disableExporter(exporterId))
          .failsWithin(Duration.ofMillis(100))
          .withThrowableOfType(ExecutionException.class)
          .withMessageContaining("force fail");
    }
  }

  @Nested
  final class ExporterEnable {
    private final String exporterIdToEnable = "exporterA";
    private final String validExporterToInitialize = "exporterB";
    private final String exporterWithDifferentType = "exporterC";
    private final String exporterWithCustomExporterFactory = "exporterWithFactory";
    private final String exporterWithSameCustomExporterFactory = "exporterWithFactoryB";
    private final DynamicPartitionConfig partitionConfig =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of(
                    validExporterToInitialize,
                    new ExporterState(0, State.ENABLED, Optional.empty()))));

    @BeforeEach
    void setup() {
      partitionTransitionContext = new TestPartitionTransitionContext();
      partitionTransitionContext.setExporterRepository(getExporterRepository());
      partitionConfigurationManager =
          new PartitionConfigurationManager(
              LOGGER,
              partitionTransitionContext,
              partitionTransitionContext.getExportedDescriptors(),
              testConcurrencyControl);
    }

    private ExporterRepository getExporterRepository() {
      final List<ExporterDescriptor> exporters =
          List.of(
              new ExporterDescriptor(exporterIdToEnable, TestExporterA.class, Map.of()),
              new ExporterDescriptor(validExporterToInitialize, TestExporterA.class, Map.of()),
              new ExporterDescriptor(exporterWithDifferentType, TestExporterC.class, Map.of()),
              new ExporterDescriptor(exporterWithCustomExporterFactory, new TestExporterFactory()),
              new ExporterDescriptor(
                  exporterWithSameCustomExporterFactory, new TestExporterFactory()));
      return new ExporterRepository(exporters, new ExternalJarRepository());
    }

    @Test
    void shouldEnableExporterAndUpdateConfigInContext() {
      // given
      partitionTransitionContext.setDynamicPartitionConfig(partitionConfig);
      final ExporterDirector mockExporterDirector = mock(ExporterDirector.class);
      when(mockExporterDirector.enableExporter(any(), any(), any()))
          .thenReturn(testConcurrencyControl.createCompletedFuture());
      partitionTransitionContext.setExporterDirector(mockExporterDirector);

      // when
      partitionConfigurationManager.enableExporter(exporterIdToEnable, 1, null).join();

      // then
      assertThat(
              partitionTransitionContext
                  .getDynamicPartitionConfig()
                  .exporting()
                  .exporters()
                  .get(exporterIdToEnable)
                  .state())
          .describedAs("Exporter state should be updated in the context")
          .isEqualTo(State.ENABLED);
      verify(mockExporterDirector)
          .enableExporter(
              eq(exporterIdToEnable), eq(new ExporterInitializationInfo(1, null)), any());
    }

    @Test
    void shouldEnableExporterWithCustomFactoryAndUpdateConfigInContext() {
      // given
      partitionTransitionContext.setDynamicPartitionConfig(partitionConfig);
      final ExporterDirector mockExporterDirector = mock(ExporterDirector.class);
      when(mockExporterDirector.enableExporter(any(), any(), any()))
          .thenReturn(testConcurrencyControl.createCompletedFuture());
      partitionTransitionContext.setExporterDirector(mockExporterDirector);

      // when
      partitionConfigurationManager
          .enableExporter(exporterWithCustomExporterFactory, 1, null)
          .join();

      // then
      assertThat(
              partitionTransitionContext
                  .getDynamicPartitionConfig()
                  .exporting()
                  .exporters()
                  .get(exporterWithCustomExporterFactory)
                  .state())
          .describedAs("Exporter state should be updated in the context")
          .isEqualTo(State.ENABLED);
      verify(mockExporterDirector)
          .enableExporter(
              eq(exporterWithCustomExporterFactory),
              eq(new ExporterInitializationInfo(1, null)),
              any());
    }

    @Test
    void shouldEnableExporterWithTheInitializationFromAnotherExporter() {
      // given
      partitionTransitionContext.setDynamicPartitionConfig(partitionConfig);
      final ExporterDirector mockExporterDirector = mock(ExporterDirector.class);
      when(mockExporterDirector.enableExporter(any(), any(), any()))
          .thenReturn(testConcurrencyControl.createCompletedFuture());
      partitionTransitionContext.setExporterDirector(mockExporterDirector);

      // when
      partitionConfigurationManager
          .enableExporter(exporterIdToEnable, 2, validExporterToInitialize)
          .join();

      // then
      final ExporterState exporterState =
          partitionTransitionContext
              .getDynamicPartitionConfig()
              .exporting()
              .exporters()
              .get(exporterIdToEnable);
      assertThat(exporterState)
          .extracting(
              ExporterState::state,
              ExporterState::metadataVersion,
              e -> e.initializedFrom().orElseThrow())
          .describedAs("Exporter state should be updated in the context")
          .contains(State.ENABLED, 2L, validExporterToInitialize);

      verify(mockExporterDirector)
          .enableExporter(
              eq(exporterIdToEnable),
              eq(new ExporterInitializationInfo(2, validExporterToInitialize)),
              any());
    }

    @Test
    void shouldEnableExporterWithCustomFactoryWithTheInitializationFromAnotherExporter() {
      // given
      partitionTransitionContext.setDynamicPartitionConfig(partitionConfig);
      final ExporterDirector mockExporterDirector = mock(ExporterDirector.class);
      when(mockExporterDirector.enableExporter(any(), any(), any()))
          .thenReturn(testConcurrencyControl.createCompletedFuture());
      partitionTransitionContext.setExporterDirector(mockExporterDirector);

      // when
      partitionConfigurationManager
          .enableExporter(
              exporterWithCustomExporterFactory, 2, exporterWithSameCustomExporterFactory)
          .join();

      // then
      final ExporterState exporterState =
          partitionTransitionContext
              .getDynamicPartitionConfig()
              .exporting()
              .exporters()
              .get(exporterWithCustomExporterFactory);
      assertThat(exporterState)
          .extracting(
              ExporterState::state,
              ExporterState::metadataVersion,
              e -> e.initializedFrom().orElseThrow())
          .describedAs("Exporter state should be updated in the context")
          .contains(State.ENABLED, 2L, exporterWithSameCustomExporterFactory);

      verify(mockExporterDirector)
          .enableExporter(
              eq(exporterWithCustomExporterFactory),
              eq(new ExporterInitializationInfo(2, exporterWithSameCustomExporterFactory)),
              any());
    }

    @Test
    void shouldUpdateConfigInContextWhenExporterDirectorIsNotAvailable() {
      // given
      partitionTransitionContext.setDynamicPartitionConfig(partitionConfig);

      // when
      partitionConfigurationManager.enableExporter(exporterIdToEnable, 1, null).join();

      // then
      assertThat(
              partitionTransitionContext
                  .getDynamicPartitionConfig()
                  .exporting()
                  .exporters()
                  .get(exporterIdToEnable)
                  .state())
          .describedAs("Exporter state should be updated in the context")
          .isEqualTo(State.ENABLED);
    }

    @Test
    void shouldFailFutureIfEnablingExporterFailed() {
      // given
      partitionTransitionContext.setDynamicPartitionConfig(partitionConfig);
      final ExporterDirector mockExporterDirector = mock(ExporterDirector.class);
      when(mockExporterDirector.enableExporter(any(), any(), any()))
          .thenReturn(testConcurrencyControl.failedFuture(new RuntimeException("force fail")));
      partitionTransitionContext.setExporterDirector(mockExporterDirector);

      // when - then
      assertThat(partitionConfigurationManager.enableExporter(exporterIdToEnable, 1, null))
          .failsWithin(Duration.ofMillis(100))
          .withThrowableOfType(ExecutionException.class)
          .withMessageContaining("force fail");
    }

    @Test
    void shouldFailWhenExporterDescriptorIsNotAvailable() {
      // when
      final var result = partitionConfigurationManager.enableExporter("invalid-id", 1, null);

      // then
      assertThat(result)
          .failsWithin(Duration.ofMillis(100))
          .withThrowableThat()
          .withMessageContaining("Exporter configuration of 'invalid-id' not found");
    }

    @Test
    void shouldFailWhenExporterToInitializeFromDoesNotExist() {
      // when
      final var result =
          partitionConfigurationManager.enableExporter(exporterIdToEnable, 1, "invalid-id");

      // then
      assertThat(result)
          .failsWithin(Duration.ofMillis(100))
          .withThrowableThat()
          .withMessageContaining("Exporter configuration of 'invalid-id' not found");
    }

    @Test
    void shouldFailIfExporterToInitializeFromIsNotSameType() {
      // when
      final var result =
          partitionConfigurationManager.enableExporter(
              exporterIdToEnable, 1, exporterWithDifferentType);

      // then
      assertThat(result)
          .failsWithin(Duration.ofMillis(100))
          .withThrowableThat()
          .withMessageContaining(
              "Exporter 'exporterA' is not of the same type as exporter 'exporterC'");
    }

    private static final class TestExporterA implements Exporter {
      @Override
      public void export(final Record<?> record) {}
    }

    private static final class TestExporterC implements Exporter {
      @Override
      public void export(final Record<?> record) {}
    }
  }
}
