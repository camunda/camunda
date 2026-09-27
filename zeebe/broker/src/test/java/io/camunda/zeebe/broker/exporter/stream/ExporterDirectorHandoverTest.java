/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.atomix.raft.protocol.ExporterPosition;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.util.ControlledTestExporter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class ExporterDirectorHandoverTest {

  private static final String EXPORTER_ID_1 = "exporter-1";
  private static final String EXPORTER_ID_2 = "exporter-2";
  private static final long LEADER_TERM = 5;

  private final ExporterStateHandover handover = new ExporterStateHandover();

  @Rule
  public final ExporterRule rule =
      ExporterRule.activeExporter()
          .withExporterDirectorContextConfigurator(
              context -> context.exporterStateHandover(handover).leaderTerm(LEADER_TERM));

  @Rule
  public final ExporterRule passiveRule =
      ExporterRule.passiveExporter()
          .withExporterDirectorContextConfigurator(
              context -> context.exporterStateHandover(handover).leaderTerm(LEADER_TERM));

  private final List<ControlledTestExporter> exporters = new ArrayList<>();
  private final List<ExporterDescriptor> exporterDescriptors = new ArrayList<>();
  private final Map<String, Optional<byte[]>> metadataAtOpen = new ConcurrentHashMap<>();

  @Before
  public void init() {
    createExporter(EXPORTER_ID_1);
    createExporter(EXPORTER_ID_2);
  }

  @Test
  public void shouldApplyTheHandedOverPositionsBeforeOpeningTheExporters() {
    // given
    final long position1 = writeEvent();
    final long position2 = writeEvent();
    handover.receive(
        LEADER_TERM - 1,
        List.of(
            position(EXPORTER_ID_1, position1, "e1"), position(EXPORTER_ID_2, position1, "e2")));

    // when
    rule.startExporterDirector(exporterDescriptors);

    // then
    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 1);
    waitUntil(() -> exporters.get(1).getExportedRecords().size() == 1);
    assertThat(metadataAtOpen.get(EXPORTER_ID_1)).hasValueSatisfying(hasMetadata("e1"));
    assertThat(metadataAtOpen.get(EXPORTER_ID_2)).hasValueSatisfying(hasMetadata("e2"));
    assertThat(exporters.get(0).getExportedRecords())
        .extracting(Record::getPosition)
        .containsExactly(position2);
    assertThat(exporters.get(1).getExportedRecords())
        .extracting(Record::getPosition)
        .containsExactly(position2);
  }

  @Test
  public void shouldIgnoreAHandedOverPositionLowerThanItsOwn() throws Exception {
    // given
    final long position1 = writeEvent();
    final long position2 = writeEvent();
    rule.startExporterDirector(exporterDescriptors);
    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 2);
    exporters.get(0).getController().updateLastExportedRecordPosition(position2, bytes("own"));
    Awaitility.await("the acknowledged position is persisted")
        .until(() -> rule.getExportersState().getPosition(EXPORTER_ID_1) == position2);
    rule.closeExporterDirector();
    metadataAtOpen.clear();
    handover.receive(LEADER_TERM - 1, List.of(position(EXPORTER_ID_1, position1, "handed-over")));

    // when
    rule.startExporterDirector(exporterDescriptors);

    // then
    awaitOpened(EXPORTER_ID_1);
    assertThat(rule.getExportersState().getPosition(EXPORTER_ID_1)).isEqualTo(position2);
    assertThat(metadataAtOpen.get(EXPORTER_ID_1)).hasValueSatisfying(hasMetadata("own"));
  }

  @Test
  public void shouldIgnoreTheHandedOverPositionOfAnUnconfiguredExporter() {
    // given
    final long position = writeEvent();
    handover.receive(
        LEADER_TERM - 1,
        List.of(position(EXPORTER_ID_1, position, "e1"), position("unconfigured", position, "x")));

    // when
    rule.startExporterDirector(exporterDescriptors);

    // then
    awaitOpened(EXPORTER_ID_1);
    final var state = rule.getExportersState();
    assertThat(state.getPosition(EXPORTER_ID_1)).isEqualTo(position);
    assertThat(state.getPosition("unconfigured")).isEqualTo(ExportersState.VALUE_NOT_FOUND);
  }

  @Test
  public void shouldIgnoreAHandoverFromAnotherTerm() {
    // given
    final long position = writeEvent();
    handover.receive(LEADER_TERM - 2, List.of(position(EXPORTER_ID_1, position, "e1")));

    // when
    rule.startExporterDirector(exporterDescriptors);

    // then
    awaitOpened(EXPORTER_ID_1);
    assertThat(rule.getExportersState().getPosition(EXPORTER_ID_1)).isEqualTo(-1);
    assertThat(metadataAtOpen.get(EXPORTER_ID_1)).isEmpty();
  }

  @Test
  public void shouldMirrorTheActiveDirectorsWritesToTheOutgoingPositions() throws Exception {
    // given
    final long position1 = writeEvent();
    final long position2 = writeEvent();
    rule.startExporterDirector(exporterDescriptors);
    waitUntil(() -> exporters.get(0).getExportedRecords().size() == 2);

    // when
    exporters.get(0).getController().updateLastExportedRecordPosition(position2, bytes("e1"));

    // then
    Awaitility.await("the acknowledged position is handed over")
        .untilAsserted(
            () ->
                assertThat(handover.outgoing()).contains(position(EXPORTER_ID_1, position2, "e1")));

    // when
    rule.closeExporterDirector();
    final var replayAccepted = new AtomicBoolean();
    exporters.get(0).onOpen(controller -> replayAccepted.set(controller.requestReplay(position1)));
    rule.startExporterDirector(exporterDescriptors);

    // then
    Awaitility.await("the replay reset is handed over").until(replayAccepted::get);
    assertThat(handover.outgoing()).contains(position(EXPORTER_ID_1, position1, "e1"));
  }

  @Test
  public void shouldNotMirrorThePassiveDirectorsWrites() {
    // given
    passiveRule.startExporterDirector(exporterDescriptors);

    // when
    final var state = passiveRule.getExportersState();

    // then
    assertThat(state.getPosition(EXPORTER_ID_1))
        .describedAs("the passive director initialised the exporter's state")
        .isEqualTo(-1);
    assertThat(handover.outgoing()).isEmpty();
  }

  private void createExporter(final String exporterId) {
    final ControlledTestExporter exporter = spy(new ControlledTestExporter());
    exporter.onOpen(controller -> metadataAtOpen.put(exporterId, controller.readMetadata()));

    final ExporterDescriptor descriptor =
        spy(new ExporterDescriptor(exporterId, exporter.getClass(), Map.of()));
    doAnswer(c -> exporter).when(descriptor).newInstance();

    exporters.add(exporter);
    exporterDescriptors.add(descriptor);
  }

  private void awaitOpened(final String exporterId) {
    Awaitility.await("the exporter is opened").until(() -> metadataAtOpen.containsKey(exporterId));
  }

  private long writeEvent() {
    return rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
  }

  private static ExporterPosition position(
      final String exporterId, final long position, final String metadata) {
    return new ExporterPosition(exporterId, position, bytes(metadata));
  }

  private static byte[] bytes(final String value) {
    return BufferUtil.bufferAsArray(BufferUtil.wrapString(value));
  }

  private static Consumer<byte[]> hasMetadata(final String expected) {
    return metadata -> assertThat(new String(metadata)).isEqualTo(expected);
  }
}
