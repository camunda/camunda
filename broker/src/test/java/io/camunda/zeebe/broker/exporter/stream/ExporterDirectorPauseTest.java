/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.util.ControlledTestExporter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ExporterDirectorPauseTest {

  private static final long TIMEOUT = 2_000;

  @Rule public final ExporterRule passiveExporter = ExporterRule.passiveExporter();
  @Rule public final ExporterRule activeExporter = ExporterRule.activeExporter();

  private ControlledTestExporter exporter;
  private ExporterDescriptor descriptor;

  @Before
  public void init() {
    exporter = spy(new ControlledTestExporter());
    descriptor = spy(new ExporterDescriptor("exporter-1", exporter.getClass(), Map.of()));
    doAnswer(c -> exporter).when(descriptor).newInstance();
  }

  @Test
  public void shouldPauseActiveExporter() {
    // given
    activeExporter.startExporterDirector(List.of(descriptor));
    activeExporter.getDirector().pauseExporting().join();

    // when
    activeExporter.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    assertThat(activeExporter.getDirector().getPhase().join()).isEqualTo(ExporterPhase.PAUSED);

    // then
    verify(exporter, after(TIMEOUT).times(0)).export(any());
  }

  @Test
  public void shouldResumeActiveExporter() {
    // given
    activeExporter.startExporterDirector(List.of(descriptor));
    activeExporter.getDirector().pauseExporting().join();
    activeExporter.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    assertThat(activeExporter.getDirector().getPhase().join()).isEqualTo(ExporterPhase.PAUSED);

    // when
    activeExporter.getDirector().resumeExporting().join();
    assertThat(activeExporter.getDirector().getPhase().join()).isEqualTo(ExporterPhase.EXPORTING);

    // then
    verify(exporter, timeout(TIMEOUT).times(1)).export(any());
  }

  @Test
  public void shouldNotExportPassiveExporterAfterResume() {
    // given
    passiveExporter.startExporterDirector(List.of(descriptor));
    passiveExporter.getDirector().pauseExporting().join();
    passiveExporter.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    assertThat(passiveExporter.getDirector().getPhase().join()).isEqualTo(ExporterPhase.PAUSED);

    // when
    passiveExporter.getDirector().resumeExporting().join();
    assertThat(passiveExporter.getDirector().getPhase().join()).isEqualTo(ExporterPhase.EXPORTING);
    passiveExporter.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());

    // then
    verify(exporter, after(TIMEOUT).times(0)).export(any());
  }
}
