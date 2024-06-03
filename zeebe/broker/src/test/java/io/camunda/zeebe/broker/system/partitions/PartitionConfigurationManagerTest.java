/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.exporter.stream.ExporterDirector;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Duration;
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

  @BeforeEach
  void setup() {
    partitionTransitionContext = new TestPartitionTransitionContext();
    partitionConfigurationManager =
        new PartitionConfigurationManager(
            LOGGER, partitionTransitionContext, testConcurrencyControl);
  }

  @Nested
  final class ExporterDisable {
    private final String exporterId = "exporterA";
    private final DynamicPartitionConfig partitionConfig =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of(exporterId, new ExporterState(0, State.ENABLED, Optional.empty()))));

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
}
