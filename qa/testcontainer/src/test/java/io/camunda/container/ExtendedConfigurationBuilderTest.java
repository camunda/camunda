/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.container;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Exporter;
import io.camunda.configuration.SecondaryStorageType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

final class ExtendedConfigurationBuilderTest {

  @Test
  void shouldEmitEmptyMapForPristineConfig() {
    final var flat = ExtendedConfigurationBuilder.flatPropertiesFor(new Camunda());
    assertThat(flat).isEmpty();
  }

  @Test
  void shouldEmitOnlyChangedScalars() {
    // given
    final var config = new Camunda();
    config.getCluster().setSize(3);
    config.getCluster().setPartitionCount(7);

    // when
    final var flat = ExtendedConfigurationBuilder.flatPropertiesFor(config);

    // then
    assertThat(flat)
        .containsEntry("camunda.cluster.size", 3)
        .containsEntry("camunda.cluster.partition-count", 7);
  }

  @Test
  void shouldSerializeDurationAndDataSizeAsStrings() {
    // given
    final var config = new Camunda();
    config.getData().setSnapshotPeriod(Duration.ofMinutes(1));
    config.getData().getPrimaryStorage().getLogStream().setLogSegmentSize(DataSize.ofMegabytes(16));

    // when
    final var flat = ExtendedConfigurationBuilder.flatPropertiesFor(config);

    // then
    assertThat(flat)
        .containsEntry("camunda.data.snapshot-period", "PT1M")
        .containsEntry("camunda.data.primary-storage.log-stream.log-segment-size", "16MB");
  }

  @Test
  void shouldSerializeEnumsByName() {
    // given
    final var config = new Camunda();
    config.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);

    // when
    final var flat = ExtendedConfigurationBuilder.flatPropertiesFor(config);

    // then
    assertThat(flat).containsEntry("camunda.data.secondary-storage.type", "rdbms");
  }

  @Test
  void shouldEmitListEntriesWithIndexNotation() {
    // given
    final var config = new Camunda();
    config.getCluster().setInitialContactPoints(List.of("host-a:26502", "host-b:26502"));

    // when
    final var flat = ExtendedConfigurationBuilder.flatPropertiesFor(config);

    // then
    assertThat(flat)
        .containsEntry("camunda.cluster.initial-contact-points[0]", "host-a:26502")
        .containsEntry("camunda.cluster.initial-contact-points[1]", "host-b:26502");
  }

  @Test
  void shouldEmitMapEntriesAsNestedKeys() {
    // given
    final var config = new Camunda();
    final var exporter = new Exporter();
    exporter.setClassName("io.camunda.test.MyExporter");
    exporter.setArgs(Map.of("connect", Map.of("url", "http://localhost:9200")));
    config.getData().getExporters().put("myExporter", exporter);

    // when
    final var flat = ExtendedConfigurationBuilder.flatPropertiesFor(config);

    // then — exporter field names are kebab-cased, but map keys (exporter id, args) stay verbatim
    assertThat(flat)
        .containsEntry("camunda.data.exporters.myExporter.class-name", "io.camunda.test.MyExporter")
        .containsEntry(
            "camunda.data.exporters.myExporter.args.connect.url", "http://localhost:9200");
  }

  @Test
  void shouldIgnoreInternalHelperFields() {
    // given
    final var config = new Camunda();
    // mutate something so the cluster section is emitted
    config.getCluster().setSize(2);

    // when
    final var flat = ExtendedConfigurationBuilder.flatPropertiesFor(config);

    // then — no `legacyPropertiesMap` or comparable internal-helper keys leak out
    assertThat(flat.keySet())
        .noneMatch(k -> k.contains("legacy-properties-map"))
        .noneMatch(k -> k.endsWith(".prefix"))
        .noneMatch(k -> k.endsWith(".database-name"));
  }
}
