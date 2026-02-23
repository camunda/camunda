/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.groups.Tuple;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class ExportersStateTest {

  private static final UnsafeBuffer EMPTY_METADATA = new UnsafeBuffer();

  private final AutoCloseableRule autoCloseableRule = new AutoCloseableRule();
  private final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Rule
  public final RuleChain chain = RuleChain.outerRule(temporaryFolder).around(autoCloseableRule);

  private ExportersState state;
  private ZeebeDb<ZbColumnFamilies> db;

  @Before
  public void setup() throws Exception {
    final File dbDirectory = temporaryFolder.newFolder();

    db = DefaultZeebeDbFactory.defaultFactory().createDb(dbDirectory);
    state = new ExportersState(db, db.createContext());
  }

  @After
  public void tearDown() throws Exception {
    db.close();
  }

  @Test
  public void shouldSetExporterPosition() {
    // given
    final var exporterId = "exporter-1";
    final var exporterPosition = 10L;

    // when
    state.setPosition(exporterId, exporterPosition);

    // then
    assertThat(state.getPosition(exporterId)).isEqualTo(exporterPosition);
    assertThat(state.getExporterMetadata(exporterId)).isEqualTo(EMPTY_METADATA);
  }

  @Test
  public void shouldSetExporterState() {
    // given
    final var exporterId = "exporter-1";
    final var exporterPosition = 10L;
    final var exporterMetadata = BufferUtil.wrapString("metadata-1");

    // when
    state.setExporterState(exporterId, exporterPosition, exporterMetadata);

    // then
    assertThat(state.getPosition(exporterId)).isEqualTo(exporterPosition);
    assertThat(state.getExporterMetadata(exporterId)).isEqualTo(exporterMetadata);
  }

  @Test
  public void shouldSetExporterStateWithoutMetadata() {
    // given
    final var exporterId = "exporter-1";
    final var exporterPosition = 10L;

    // when
    state.setExporterState(exporterId, exporterPosition, null);

    // then
    assertThat(state.getPosition(exporterId)).isEqualTo(exporterPosition);
    assertThat(state.getExporterMetadata(exporterId)).isEqualTo(EMPTY_METADATA);
  }

  @Test
  public void shouldOverrideExporterState() {
    // given
    final var exporterId = "exporter-1";

    final var exporterPosition1 = 10L;
    final var exporterPosition2 = 20L;

    final var exporterMetadata1 = BufferUtil.wrapString("metadata-1");
    final var exporterMetadata2 = BufferUtil.wrapString("metadata-2");

    state.setExporterState(exporterId, exporterPosition1, exporterMetadata1);

    // when
    state.setExporterState(exporterId, exporterPosition2, exporterMetadata2);

    // then
    assertThat(state.getPosition(exporterId)).isEqualTo(exporterPosition2);
    assertThat(state.getExporterMetadata(exporterId)).isEqualTo(exporterMetadata2);
  }

  @Test
  public void shouldNotClearMetadataIfEmpty() {
    // given
    final var exporterId = "e1";
    final var exporterMetadata1 = BufferUtil.wrapString("metadata-1");
    final var exporterPosition1 = 10L;

    state.setExporterState(exporterId, exporterPosition1, exporterMetadata1);

    // when
    state.setExporterState(exporterId, exporterPosition1, null);

    // then
    assertThat(state.getPosition(exporterId)).isEqualTo(exporterPosition1);
    assertThat(state.getExporterMetadata(exporterId)).isEqualTo(exporterMetadata1);
  }

  @Test
  public void shouldSetExporterStateWithAndWithoutMetadata() {
    // given
    final var exporterId1 = "e1";
    final var exporterMetadata1 = BufferUtil.wrapString("metadata-1");
    final var exporterPosition1 = 10L;

    final var exporterId2 = "e2";
    final var exporterPosition2 = 20L;

    // when
    state.setExporterState(exporterId1, exporterPosition1, exporterMetadata1);
    state.setExporterState(exporterId2, exporterPosition2, null);

    // then
    assertThat(state.getPosition(exporterId1)).isEqualTo(exporterPosition1);
    assertThat(state.getExporterMetadata(exporterId1)).isEqualTo(exporterMetadata1);
    assertThat(state.getPosition(exporterId2)).isEqualTo(exporterPosition2);
    assertThat(state.getExporterMetadata(exporterId2)).isEqualTo(EMPTY_METADATA);
  }

  @Test
  public void shouldReturnUnknownPositionForUnknownExporter() {
    // given
    final String id = "exporter";

    // when
    final long position = state.getPosition(id);

    // then
    assertThat(position).isEqualTo(ExportersState.VALUE_NOT_FOUND);
  }

  @Test
  public void shouldReturnEmptyMetadataForUnknownExporter() {
    // when
    final var exporterMetadata = state.getExporterMetadata("unknown-exporter");

    // then
    assertThat(exporterMetadata)
        .describedAs("Expect that the metadata is empty if the exporter is unknown")
        .isEqualTo(EMPTY_METADATA);
  }

  @Test
  public void shouldRemoveExporterState() {
    // given
    final var exporterId1 = "e1";
    final var exporterMetadata1 = BufferUtil.wrapString("metadata-1");
    final var exporterPosition1 = 10L;

    final var exporterId2 = "e2";
    final var exporterPosition2 = 20L;
    final var exporterMetadata2 = BufferUtil.wrapString("metadata-2");

    state.setExporterState(exporterId1, exporterPosition1, exporterMetadata1);
    state.setExporterState(exporterId2, exporterPosition2, exporterMetadata2);

    // when
    state.removeExporterState(exporterId2);

    // then
    assertThat(state.getPosition(exporterId2)).isEqualTo(ExportersState.VALUE_NOT_FOUND);
    assertThat(state.getExporterMetadata(exporterId2)).isEqualTo(EMPTY_METADATA);

    assertThat(state.getPosition(exporterId1)).isEqualTo(exporterPosition1);
    assertThat(state.getExporterMetadata(exporterId1)).isEqualTo(exporterMetadata1);
  }

  @Test
  public void shouldVisitExporterState() {
    // given
    final var exporterId1 = "e1";
    final var exporterMetadata1 = BufferUtil.wrapString("metadata-1");
    final var exporterPosition1 = 10L;

    final var exporterId2 = "e2";
    final var exporterPosition2 = 20L;
    final var exporterMetadata2 = BufferUtil.wrapString("metadata-2");

    state.setExporterState(exporterId1, exporterPosition1, exporterMetadata1);
    state.setExporterState(exporterId2, exporterPosition2, exporterMetadata2);

    // when
    final Map<String, Tuple> exporterState = new HashMap<>();
    state.visitExporterState(
        (exporterId, exporterStateEntry) ->
            exporterState.put(
                exporterId,
                tuple(
                    exporterStateEntry.getPosition(),
                    BufferUtil.cloneBuffer(exporterStateEntry.getMetadata()))));

    // then
    assertThat(exporterState)
        .hasSize(2)
        .contains(
            entry(exporterId1, tuple(exporterPosition1, exporterMetadata1)),
            entry(exporterId2, tuple(exporterPosition2, exporterMetadata2)));
  }

  @Test
  public void shouldGetLowestPosition() {
    // given
    state.setPosition("e2", -1L);
    state.setPosition("e1", 1L);

    // when/then
    assertThat(state.getLowestPosition()).isEqualTo(-1L);
  }

  @Test
  public void shouldClearState() {
    // given
    final var exporterId = "e2";
    state.setExporterState(exporterId, 1L, BufferUtil.wrapString("metadata"));

    // when
    state.removeExporterState(exporterId);

    // then
    assertThat(state.hasExporters()).isFalse();
    assertThat(state.getLowestPosition()).isEqualTo(-1L);
  }

  @Test
  public void shouldGetHighestPosition() {
    // given
    state.setPosition("e1", 1L);
    state.setPosition("e2", 100L);
    state.setPosition("e3", 50L);

    // when/then
    assertThat(state.getHighestPosition()).isEqualTo(100L);
  }

  @Test
  public void shouldReturnMinValueForHighestPositionWhenNoExporters() {
    // when/then
    assertThat(state.getHighestPosition()).isEqualTo(Long.MIN_VALUE);
  }
}
