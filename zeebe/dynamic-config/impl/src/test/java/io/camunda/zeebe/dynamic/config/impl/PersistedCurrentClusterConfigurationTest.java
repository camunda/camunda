/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.PersistedCurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.PersistedCurrentClusterConfiguration.ChecksumMismatch;
import io.camunda.zeebe.dynamic.config.PersistedCurrentClusterConfiguration.MissingHeader;
import io.camunda.zeebe.dynamic.config.PersistedCurrentClusterConfiguration.UnexpectedVersion;
import io.camunda.zeebe.dynamic.config.impl.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.util.DefaultTenantConfiguration;
import io.camunda.zeebe.util.FileUtil;
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
    return DefaultTenantConfiguration.init()
        .addMember(MemberId.from("0"), Map.of(1, PartitionState.active(1, partitionConfig)))
        .addMember(MemberId.from("1"))
        .configuration();
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
  void shouldKeepPreviousConfigurationOnDiskWhenWriteFails() throws IOException {
    // given - a persisted configuration, and a write of the next one that fails
    final var file = tmp.resolve("config.meta");
    final var previous = sampleConfiguration();
    final var persisted = PersistedCurrentClusterConfiguration.ofFile(file, serializer);
    persisted.update(previous);
    // the new file cannot be created if a directory is in its place
    Files.createDirectory(tmp.resolve("config.meta.tmp"));

    // when
    final var thrown =
        catchThrowable(() -> persisted.update(CurrentClusterConfiguration.uninitialized()));

    // then - neither used nor gossiped, and still on disk after a restart
    assertThat(thrown).isInstanceOf(IOException.class);
    assertThat(persisted.getConfiguration()).isEqualTo(previous);
    assertThat(PersistedCurrentClusterConfiguration.ofFile(file, serializer).getConfiguration())
        .isEqualTo(previous);
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
  void shouldRewriteUnchangedConfigurationAfterFailedWrite() throws IOException {
    // given - the new file replaced the old one, but syncing the directory failed
    final var file = tmp.resolve("config.meta");
    final var previous = sampleConfiguration();
    final var persisted = PersistedCurrentClusterConfiguration.ofFile(file, serializer);
    persisted.update(previous);
    try (final var fileUtil = mockStatic(FileUtil.class, CALLS_REAL_METHODS)) {
      fileUtil
          .when(() -> FileUtil.flushDirectory(tmp))
          .thenThrow(new IOException("Input/output error"));
      assertThatThrownBy(() -> persisted.update(CurrentClusterConfiguration.uninitialized()))
          .isInstanceOf(IOException.class);
    }

    // when - updating to the configuration in memory, which is not the one in the file
    persisted.update(previous);

    // then
    assertThat(PersistedCurrentClusterConfiguration.ofFile(file, serializer).getConfiguration())
        .isEqualTo(previous);
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
        DefaultTenantConfiguration.init()
            .addMember(MemberId.from("0"), Map.of(1, PartitionState.active(2, partitionConfig)))
            .configuration();
    final var changed =
        DefaultTenantConfiguration.init()
            .addMember(MemberId.from("0"), Map.of(1, PartitionState.active(3, partitionConfig)))
            .configuration();
    PersistedCurrentClusterConfiguration.ofFile(file, serializer).update(initial);

    // when — keep the same header but overwrite the body in place with different content of the
    // same length
    final var fileContent = Files.readAllBytes(file);
    final var newBody = serializer.encodeCurrentClusterConfiguration(changed);
    System.arraycopy(
        newBody,
        0,
        fileContent,
        PersistedCurrentClusterConfiguration.Header.LENGTH,
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
  void shouldRejectLegacyVersion1File() throws IOException {
    // given — a file with the legacy header version 1, which every 8.10 broker has already
    // migrated to version 2
    final var file = tmp.resolve("config.meta");
    PersistedCurrentClusterConfiguration.ofFile(file, serializer).update(sampleConfiguration());
    Files.write(file, new byte[] {1}, StandardOpenOption.WRITE);

    // then — the operator is told how to get the file migrated
    assertThatCode(() -> PersistedCurrentClusterConfiguration.ofFile(file, serializer))
        .isInstanceOf(UnexpectedVersion.class)
        .hasMessageContaining("Start this broker on 8.10 first");
  }

  @Test
  void shouldRejectUnknownHeaderVersion() throws IOException {
    // given — a header version that is not the current (2) format
    final var file = tmp.resolve("config.meta");
    PersistedCurrentClusterConfiguration.ofFile(file, serializer).update(sampleConfiguration());
    Files.write(file, new byte[] {3}, StandardOpenOption.WRITE);

    // then
    assertThatCode(() -> PersistedCurrentClusterConfiguration.ofFile(file, serializer))
        .isInstanceOf(UnexpectedVersion.class);
  }
}
