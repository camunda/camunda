/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration.ChecksumMismatch;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration.MissingHeader;
import io.camunda.zeebe.dynamic.config.PersistedClusterConfiguration.UnexpectedVersion;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PersistedCurrentClusterConfigurationTest {

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  @TempDir private Path tmp;

  private CurrentClusterConfiguration sampleConfiguration() {
    final var legacy =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("0"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))))
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()));
    return CurrentClusterConfiguration.fromLegacy(legacy);
  }

  @Test
  void shouldBeUninitializedWhenFileDoesNotExist() {
    // given
    final var file = tmp.resolve("config.meta");

    // when
    final var persisted = PersistedCurrentClusterConfiguration.ofFile(file, serializer);

    // then
    assertThat(persisted.isUninitialized()).isTrue();
  }

  @Test
  void shouldPersistAndReloadVersion2() throws IOException {
    // given
    final var file = tmp.resolve("config.meta");
    final var config = sampleConfiguration();
    PersistedCurrentClusterConfiguration.ofFile(file, serializer).update(config);

    // when — reload from disk
    final var reloaded = PersistedCurrentClusterConfiguration.ofFile(file, serializer);

    // then
    assertThat(reloaded.isUninitialized()).isFalse();
    assertThat(reloaded.getConfiguration()).isEqualTo(config);
  }

  @Test
  void shouldWriteHeaderVersion2() throws IOException {
    // given
    final var file = tmp.resolve("config.meta");

    // when
    PersistedCurrentClusterConfiguration.ofFile(file, serializer).update(sampleConfiguration());

    // then — the first header byte is the new version
    assertThat(Files.readAllBytes(file)[0]).isEqualTo(PersistedCurrentClusterConfiguration.VERSION);
  }

  @Test
  void shouldMigrateLegacyVersion1FileOnRead() throws IOException {
    // given — a legacy (version 1) file, as the legacy persistence code used to write it
    final var file = tmp.resolve("config.meta");
    final var legacy =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("0"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));
    writeLegacyVersion1File(file, legacy);
    assertThat(Files.readAllBytes(file)[0]).isEqualTo((byte) 1);

    // when — read it through the new persistence class
    final var persisted = PersistedCurrentClusterConfiguration.ofFile(file, serializer);

    // then — it is migrated to the new model, equivalent to fromLegacy
    assertThat(persisted.isUninitialized()).isFalse();
    assertThat(persisted.getConfiguration())
        .isEqualTo(CurrentClusterConfiguration.fromLegacy(legacy));
  }

  @Test
  void shouldWriteBackAsVersion2AfterMigration() throws IOException {
    // given — a legacy version 1 file
    final var file = tmp.resolve("config.meta");
    final var legacy =
        ClusterConfiguration.init()
            .addMember(MemberId.from("0"), MemberState.initializeAsActive(Map.of()));
    writeLegacyVersion1File(file, legacy);

    // when — read (migrate) and persist a change
    final var persisted = PersistedCurrentClusterConfiguration.ofFile(file, serializer);

    // then — the file is now version 2 and reloads to the updated configuration
    assertThat(Files.readAllBytes(file)[0]).isEqualTo(PersistedCurrentClusterConfiguration.VERSION);
    assertThat(PersistedCurrentClusterConfiguration.ofFile(file, serializer).getConfiguration())
        .isEqualTo(persisted.getConfiguration());
  }

  @Test
  void shouldDetectChecksumMismatch() throws IOException {
    // given
    final var file = tmp.resolve("config.meta");
    PersistedCurrentClusterConfiguration.ofFile(file, serializer).update(sampleConfiguration());

    // when — corrupt the body
    Files.write(file, "extra".getBytes(), StandardOpenOption.APPEND);

    // then
    assertThatCode(() -> PersistedCurrentClusterConfiguration.ofFile(file, serializer))
        .isInstanceOf(ChecksumMismatch.class);
  }

  @Test
  void shouldDetectChecksumMismatchOnSameLengthBodyCorruption() throws IOException {
    // given — two configurations that serialize to the same length, differing in one field
    final var file = tmp.resolve("config.meta");
    final var initial =
        CurrentClusterConfiguration.fromLegacy(
            ClusterConfiguration.init()
                .addMember(
                    MemberId.from("0"),
                    MemberState.initializeAsActive(
                        Map.of(1, PartitionState.active(2, partitionConfig)))));
    final var changed =
        CurrentClusterConfiguration.fromLegacy(
            ClusterConfiguration.init()
                .addMember(
                    MemberId.from("0"),
                    MemberState.initializeAsActive(
                        Map.of(1, PartitionState.active(3, partitionConfig)))));
    PersistedCurrentClusterConfiguration.ofFile(file, serializer).update(initial);

    // when — keep the same header but overwrite the body in place with different content of the
    // same length
    final var fileContent = Files.readAllBytes(file);
    final var newBody = serializer.encodeCurrentClusterConfiguration(changed);
    System.arraycopy(
        newBody,
        0,
        fileContent,
        PersistedClusterConfiguration.Header.HEADER_LENGTH,
        newBody.length);
    Files.write(file, fileContent, StandardOpenOption.WRITE);

    // then — checksum mismatch is detected even though the file length is unchanged
    assertThatCode(() -> PersistedCurrentClusterConfiguration.ofFile(file, serializer))
        .isInstanceOf(ChecksumMismatch.class);
  }

  @Test
  void shouldFailOnEmptyFile() throws IOException {
    // given
    final var file = tmp.resolve("config.meta");
    Files.createFile(file);

    // then
    assertThatCode(() -> PersistedCurrentClusterConfiguration.ofFile(file, serializer))
        .isInstanceOf(MissingHeader.class);
  }

  @Test
  void shouldFailOnTooShortHeader() throws IOException {
    // given — fewer bytes than the header (version byte + checksum long) requires
    final var file = tmp.resolve("config.meta");
    Files.write(file, new byte[] {1, 2}, StandardOpenOption.WRITE, StandardOpenOption.CREATE);

    // then
    assertThatCode(() -> PersistedCurrentClusterConfiguration.ofFile(file, serializer))
        .isInstanceOf(MissingHeader.class);
  }

  @Test
  void shouldRejectUnknownHeaderVersion() throws IOException {
    // given — a header version that is neither the legacy (1) nor the current (2) format
    final var file = tmp.resolve("config.meta");
    PersistedCurrentClusterConfiguration.ofFile(file, serializer).update(sampleConfiguration());
    Files.write(file, new byte[] {3}, StandardOpenOption.WRITE);

    // then
    assertThatCode(() -> PersistedCurrentClusterConfiguration.ofFile(file, serializer))
        .isInstanceOf(UnexpectedVersion.class);
  }

  /**
   * Writes {@code legacy} to {@code file} exactly as the (now removed) legacy persistence class
   * used to: header version 1, followed by the checksummed, serialized legacy configuration.
   */
  private void writeLegacyVersion1File(final Path file, final ClusterConfiguration legacy)
      throws IOException {
    PersistedClusterConfiguration.writeToFile(serializer.encode(legacy), file);
  }
}
