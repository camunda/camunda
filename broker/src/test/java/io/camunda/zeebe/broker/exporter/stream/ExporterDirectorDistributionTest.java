/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.util.ControlledTestExporter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ExporterDirectorDistributionTest {

  private static final String EXPORTER_ID_1 = "exporter-1";
  private static final String EXPORTER_ID_2 = "exporter-2";

  private static final Duration DISTRIBUTION_INTERVAL = Duration.ofSeconds(15);

  private final SimplePartitionMessageService simplePartitionMessageService =
      new SimplePartitionMessageService();

  @Rule
  public final ExporterRule activeExporters =
      ExporterRule.activeExporter()
          .withPartitionMessageService(simplePartitionMessageService)
          .withDistributionInterval(DISTRIBUTION_INTERVAL);

  @Rule
  public final ExporterRule passiveExporters =
      ExporterRule.passiveExporter().withPartitionMessageService(simplePartitionMessageService);

  private final List<ControlledTestExporter> exporters = new ArrayList<>();
  private final List<ExporterDescriptor> exporterDescriptors = new ArrayList<>();

  @Before
  public void init() {
    exporters.clear();
    exporterDescriptors.clear();

    createExporter(EXPORTER_ID_1, Collections.singletonMap("x", 1));
    createExporter(EXPORTER_ID_2, Collections.singletonMap("y", 2));
  }

  private void createExporter(final String exporterId, final Map<String, Object> arguments) {
    final ControlledTestExporter exporter = spy(new ControlledTestExporter());

    final ExporterDescriptor descriptor =
        spy(new ExporterDescriptor(exporterId, exporter.getClass(), arguments));
    doAnswer(c -> exporter).when(descriptor).newInstance();

    exporters.add(exporter);
    exporterDescriptors.add(descriptor);
  }

  private void startExporters(final List<ExporterDescriptor> exporterDescriptors) {
    activeExporters.startExporterDirector(exporterDescriptors);
    passiveExporters.startExporterDirector(exporterDescriptors);
  }

  @Test
  public void shouldDistributeExporterPositions() {
    // given
    final ControlledTestExporter exporter = exporters.get(1);
    exporters.forEach(e -> e.shouldAutoUpdatePosition(true));
    startExporters(exporterDescriptors);

    final long position =
        activeExporters.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());

    Awaitility.await("director has read all records until now")
        .untilAsserted(() -> assertThat(exporter.getExportedRecords()).hasSize(1));

    final var activeExporterState = activeExporters.getExportersState();
    assertThat(activeExporterState.getPosition(EXPORTER_ID_1)).isEqualTo(position);
    assertThat(activeExporterState.getPosition(EXPORTER_ID_2)).isEqualTo(position);

    final var passiveExporterState = passiveExporters.getExportersState();
    assertThat(passiveExporterState.getPosition(EXPORTER_ID_1)).isEqualTo(-1);
    assertThat(passiveExporterState.getPosition(EXPORTER_ID_2)).isEqualTo(-1);

    // when
    activeExporters.getClock().addTime(DISTRIBUTION_INTERVAL);

    // then
    Awaitility.await("Active Director has distributed positions and passive has received it")
        .untilAsserted(
            () -> {
              assertThat(passiveExporterState.getPosition(EXPORTER_ID_1)).isEqualTo(position);
              assertThat(passiveExporterState.getPosition(EXPORTER_ID_2)).isEqualTo(position);
            });
  }

  @Test
  public void shouldNotResetExporterPositionWhenOldPositionReceived() throws Exception {
    // given
    exporters.forEach(e -> e.shouldAutoUpdatePosition(true));
    startExporters(exporterDescriptors);
    final long position = 10L;

    activeExporters.getExportersState().setPosition(EXPORTER_ID_1, position);
    activeExporters.getExportersState().setPosition(EXPORTER_ID_2, position);

    activeExporters.getClock().addTime(DISTRIBUTION_INTERVAL);

    final var passiveExporterState = passiveExporters.getExportersState();
    Awaitility.await("Active Director has distributed positions and passive has received it")
        .untilAsserted(
            () -> {
              assertThat(passiveExporterState.getPosition(EXPORTER_ID_1)).isEqualTo(position);
              assertThat(passiveExporterState.getPosition(EXPORTER_ID_2)).isEqualTo(position);
            });

    // when
    activeExporters.getExportersState().setPosition(EXPORTER_ID_1, position - 1);
    activeExporters.getExportersState().setPosition(EXPORTER_ID_2, position + 1);

    activeExporters.getClock().addTime(DISTRIBUTION_INTERVAL);
    Awaitility.await("Active Director has distributed positions and passive has received it")
        .untilAsserted(
            () ->
                assertThat(passiveExporterState.getPosition(EXPORTER_ID_2))
                    .isEqualTo(position + 1));

    // then - the exported position should not go back
    assertThat(passiveExporterState.getPosition(EXPORTER_ID_1)).isEqualTo(position);
  }
}
