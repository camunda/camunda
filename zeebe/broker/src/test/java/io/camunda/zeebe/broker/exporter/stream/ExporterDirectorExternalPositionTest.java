/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.util.ControlledTestExporter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

public final class ExporterDirectorExternalPositionTest {

  private static final String FLOOR = "event-plane";
  private static final DirectBuffer METADATA = BufferUtil.wrapString("{\"index\":7}");
  private static final Duration DISTRIBUTION_INTERVAL = Duration.ofSeconds(15);

  private final SimplePartitionMessageService messageService = new SimplePartitionMessageService();

  @Rule
  public final ExporterRule active =
      ExporterRule.activeExporter()
          .withPartitionMessageService(messageService)
          .withDistributionInterval(DISTRIBUTION_INTERVAL)
          .withExporterDirectorContextConfigurator(c -> c.externalPositionIds(Set.of(FLOOR)));

  @Rule
  public final ExporterRule passive =
      ExporterRule.passiveExporter()
          .withPartitionMessageService(messageService)
          .withExporterDirectorContextConfigurator(c -> c.externalPositionIds(Set.of(FLOOR)));

  @After
  public void tearDown() throws Exception {
    for (final var rule : List.of(active, passive)) {
      if (rule.getDirector() != null) {
        rule.closeExporterDirector();
      }
    }
  }

  @Test
  public void shouldHoldCompactionUntilTheFirstPositionIsSet() {
    // when
    active.startExporterDirector(List.of());
    passive.startExporterDirector(List.of());

    // then
    final var position = active.getDirector().getExternalPosition(FLOOR).join();
    assertThat(position.position()).isEqualTo(-1);
    assertThat(position.metadata()).isEmpty();
    for (final var state : List.of(active.getExportersState(), passive.getExportersState())) {
      assertThat(state.hasExporters()).isTrue();
      assertThat(state.getLowestPosition()).isEqualTo(-1);
    }
  }

  @Test
  public void shouldDistributeTheExternalPositionWithoutExporters() {
    // given
    active.startExporterDirector(List.of());
    passive.startExporterDirector(List.of());

    // when
    active.getDirector().setExternalPosition(FLOOR, 10, METADATA).join();

    // then
    final var passiveState = passive.getExportersState();
    Awaitility.await("the passive director has the external position")
        .untilAsserted(
            () -> {
              active.getClock().addTime(DISTRIBUTION_INTERVAL);
              assertThat(passiveState.getPosition(FLOOR)).isEqualTo(10);
            });
    assertThat(passiveState.getExporterMetadata(FLOOR)).isEqualTo(METADATA);
    assertThat(passiveState.getLowestPosition()).isEqualTo(10);
  }

  @Test
  public void shouldReturnTheExternalPositionAndMetadata() {
    // given
    active.startExporterDirector(List.of());

    // when
    active.getDirector().setExternalPosition(FLOOR, 10, METADATA).join();

    // then
    final var position = active.getDirector().getExternalPosition(FLOOR).join();
    assertThat(position.position()).isEqualTo(10);
    assertThat(position.metadata()).isEqualTo(BufferUtil.bufferAsArray(METADATA));
  }

  @Test
  public void shouldBoundTheLowestPositionWithExporters() {
    // given
    final var exporter = spy(new ControlledTestExporter());
    exporter.onExport(
        record -> exporter.getController().updateLastExportedRecordPosition(record.getPosition()));
    final var descriptor = spy(new ExporterDescriptor("exporter", exporter.getClass(), Map.of()));
    doAnswer(c -> exporter).when(descriptor).newInstance();
    active.startExporterDirector(List.of(descriptor));
    final long first = active.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    final long second = active.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    final var state = active.getExportersState();
    Awaitility.await("the exporter has exported both records")
        .untilAsserted(() -> assertThat(state.getPosition("exporter")).isEqualTo(second));

    // when
    active.getDirector().setExternalPosition(FLOOR, first, METADATA).join();

    // then
    assertThat(state.getLowestPosition()).isEqualTo(first);
  }

  @Test
  public void shouldRejectAnUnknownId() {
    // given
    active.startExporterDirector(List.of());

    // when
    final var result = active.getDirector().setExternalPosition("other", 10, METADATA);

    // then
    assertThatThrownBy(result::join)
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected one of the external position ids [event-plane]");
  }

  @Test
  public void shouldRejectAPositionGoingBack() {
    // given
    active.startExporterDirector(List.of());
    active.getDirector().setExternalPosition(FLOOR, 10, METADATA).join();

    // when
    final var result = active.getDirector().setExternalPosition(FLOOR, 9, METADATA);

    // then
    assertThatThrownBy(result::join)
        .hasRootCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("to move forward from 10, but got 9");
    assertThat(active.getDirector().getExternalPosition(FLOOR).join().position()).isEqualTo(10);
  }

  @Test
  public void shouldRejectSettingThePositionOnAFollower() {
    // given
    passive.startExporterDirector(List.of());

    // when
    final var result = passive.getDirector().setExternalPosition(FLOOR, 10, METADATA);

    // then
    assertThatThrownBy(result::join)
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("on the leader, but this director is PASSIVE");
  }
}
