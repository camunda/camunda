/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ExporterDisableTest {
  private static final String EXPORTER_ID_1 = "exporter-1";
  private static final String EXPORTER_ID_2 = "exporter-2";

  @Rule public final ExporterRule rule = ExporterRule.activeExporter();
  private final Map<String, ControlledTestExporter> exporters = new HashMap<>();
  private final List<ExporterDescriptor> exporterDescriptors = new ArrayList<>();

  @Before
  public void init() {
    exporters.clear();
    exporterDescriptors.clear();

    createExporter(EXPORTER_ID_1, Collections.singletonMap("x", 1));
    createExporter(EXPORTER_ID_2, Collections.singletonMap("y", 2));
  }

  @After
  public void tearDown() throws Exception {
    rule.closeExporterDirector();
  }

  private void createExporter(final String exporterId, final Map<String, Object> arguments) {
    final ControlledTestExporter exporter = spy(new ControlledTestExporter());
    exporter.shouldAutoUpdatePosition(true);

    final ExporterDescriptor descriptor =
        spy(new ExporterDescriptor(exporterId, exporter.getClass(), arguments));
    doAnswer(c -> exporter).when(descriptor).newInstance();

    exporters.put(exporterId, exporter);
    exporterDescriptors.add(descriptor);
  }

  @Test
  public void shouldDisableExporter() {
    // given
    rule.startExporterDirector(exporterDescriptors);
    rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());

    // when
    rule.getDirector().disableExporter(EXPORTER_ID_1).join();

    rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    final long expectedLastExportedPosition =
        rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());

    // then

    Awaitility.await()
        .untilAsserted(
            () -> assertThat(exporters.get(EXPORTER_ID_2).getExportedRecords()).hasSize(3));

    assertThat(rule.getDirector().getLowestPosition().join())
        .describedAs("The lowest position should be updated when one exporter is disabled")
        .isEqualTo(expectedLastExportedPosition);

    assertThat(exporters.get(EXPORTER_ID_1).getExportedRecords())
        .describedAs("Should not export to exporter-1 after disabling")
        .hasSizeLessThanOrEqualTo(1);
  }

  @Test
  public void shouldSucceedWhenDisablingAlreadyDisabledExporter() {
    // given
    rule.startExporterDirector(exporterDescriptors);
    rule.getDirector().disableExporter(EXPORTER_ID_1).join();

    // when - then
    assertThat(rule.getDirector().disableExporter(EXPORTER_ID_1))
        .succeedsWithin(Duration.ofSeconds(5));
  }

  @Test
  public void shouldSucceedWhenDisablingNonExistingExporter() {
    // given
    rule.startExporterDirector(exporterDescriptors);

    // when - then
    assertThat(rule.getDirector().disableExporter("non-existing-exporter"))
        .succeedsWithin(Duration.ofSeconds(5));
  }

  @Test
  public void shouldSucceedDisablingWhenActorIsAlreadyClosed() {
    // give
    rule.startExporterDirector(exporterDescriptors);

    // when
    rule.getDirector().close();

    // then
    assertThat(rule.getDirector().disableExporter(EXPORTER_ID_1))
        .succeedsWithin(Duration.ofSeconds(5));
  }
}
