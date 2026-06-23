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
import io.camunda.configuration.NodeIdProvider.Type;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
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
  void shouldMirrorFixedNodeIdToLegacyNodeIdProperty() {
    // given
    final var config = new Camunda();
    config.getCluster().setNodeId(2);

    // when
    final var flat = ExtendedConfigurationBuilder.flatPropertiesFor(config);

    // then — the fixed node id is emitted under the current key and mirrored to the legacy flat
    // `camunda.cluster.node-id` so the config also binds on pre-8.9 versions
    assertThat(flat).containsEntry("camunda.cluster.node-id-provider.fixed.node-id", 2);
    assertThat(flat).containsEntry("camunda.cluster.node-id", 2);
  }

  @Test
  void shouldMirrorFixedNodeIdToLegacyNodeIdPropertyWhenSetViaFixedNodeIdProvider() {
    // given
    final var config = new Camunda();
    config.getCluster().getNodeIdProvider().fixed().setNodeId(2);

    // when
    final var flat = ExtendedConfigurationBuilder.flatPropertiesFor(config);

    // then — setting the node id directly on the fixed provider yields the same legacy mirror
    assertThat(flat).containsEntry("camunda.cluster.node-id-provider.fixed.node-id", 2);
    assertThat(flat).containsEntry("camunda.cluster.node-id", 2);
  }

  @Test
  void shouldNotMirrorLegacyNodeIdWhenNodeIdProviderIsNotFixed() {
    // given
    final var config = new Camunda();
    config.getCluster().getNodeIdProvider().setType(Type.S3);

    // when
    final var flat = ExtendedConfigurationBuilder.flatPropertiesFor(config);

    // then — a non-FIXED provider has no flat node id, so nothing is mirrored to the legacy key
    assertThat(flat)
        .containsEntry("camunda.cluster.node-id-provider.type", "S3")
        .doesNotContainKeys(
            "camunda.cluster.node-id", "camunda.cluster.node-id-provider.fixed.node-id");
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

  @Test
  void shouldEmitKeysUnderProvidedPrefix() {
    // given
    final var config = new Camunda();
    config.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);

    // when — flattened under a physical-tenant prefix instead of the top-level `camunda` header
    final var flat =
        ExtendedConfigurationBuilder.flatPropertiesFor(config, "camunda.physical-tenants.tenanta");

    // then — every key carries the given prefix and none leak under the root `camunda.data.*` form
    assertThat(flat)
        .containsEntry("camunda.physical-tenants.tenanta.data.secondary-storage.type", "rdbms");
    assertThat(flat.keySet()).noneMatch(k -> k.startsWith("camunda.data."));
  }

  @Test
  void shouldNotEmitNodeIdForPhysicalTenantConfig() {
    // given — physical-tenant configs carry storage/security only and never a node id, which is a
    // root-config concern
    final var config = new Camunda();
    config.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);

    // when — flattened under a physical-tenant prefix
    final var flat =
        ExtendedConfigurationBuilder.flatPropertiesFor(config, "camunda.physical-tenants.tenanta");

    // then — with no node id set neither the fixed nor the legacy node-id key is emitted, which is
    // why legacy-node-id mirroring needs no prefix-specific special-casing
    assertThat(flat.keySet()).noneMatch(k -> k.contains("node-id"));
  }
}
