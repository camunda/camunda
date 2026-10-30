/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The expected keys are spelled out instead of derived from the constants: they pin the persisted
 * layout, which must not change without a migration for existing backups.
 */
class BackupStorePathsTest {

  private static final BackupIdentifierImpl ID = new BackupIdentifierImpl(2, 1, 100L);
  private static final BackupIdentifierImpl ZONED_ID =
      new BackupIdentifierImpl(2, "eu-west", 1, 100L);

  @Test
  void shouldBuildMetadataPath() {
    // when
    final var path = BackupStorePaths.metadataPath(1);

    // then
    assertThat(path).isEqualTo("metadata/1/metadata.json");
  }

  @Test
  void shouldBuildMetadataPathUnderBasePath() {
    // when
    final var path = BackupStorePaths.metadataPath(Optional.of("base"), 1);

    // then
    assertThat(path).isEqualTo("base/metadata/1/metadata.json");
  }

  @Test
  void shouldBuildManifestPath() {
    // when
    final var path = BackupStorePaths.manifestPath(ID);

    // then
    assertThat(path).isEqualTo("manifests/1/100/2/manifest.json");
  }

  @Test
  void shouldBuildManifestPathUnderBasePath() {
    // when
    final var path = BackupStorePaths.manifestPath(Optional.of("base"), ID);

    // then
    assertThat(path).isEqualTo("base/manifests/1/100/2/manifest.json");
  }

  @Test
  void shouldBuildManifestPathWithZone() {
    // when
    final var path = BackupStorePaths.manifestPath(ZONED_ID);

    // then
    assertThat(path).isEqualTo("manifests/1/100/eu-west_2/manifest.json");
  }

  @Test
  void shouldBuildFileSetPath() {
    // when
    final var path = BackupStorePaths.fileSetPath(ID, BackupStorePaths.SNAPSHOT_FILESET_NAME);

    // then
    assertThat(path).isEqualTo("contents/1/100/2/snapshot/");
  }

  @Test
  void shouldBuildFileSetPathUnderBasePath() {
    // when
    final var path =
        BackupStorePaths.fileSetPath(
            Optional.of("base"), ID, BackupStorePaths.SEGMENTS_FILESET_NAME);

    // then
    assertThat(path).isEqualTo("base/contents/1/100/2/segments/");
  }

  @Test
  void shouldBuildManifestsPrefixForExactWildcard() {
    // given
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.of(BrokerMemberId.from(2)), Optional.of(1), CheckpointPattern.of(100L));

    // when
    final var prefix = BackupStorePaths.manifestsPrefix(Optional.of("base"), wildcard);

    // then
    assertThat(prefix).isEqualTo("base/manifests/1/100/2");
  }

  @Test
  void shouldBuildManifestsPrefixForAnyWildcard() {
    // given
    final var wildcard =
        new BackupIdentifierWildcardImpl(
            Optional.empty(), Optional.empty(), CheckpointPattern.any());

    // when
    final var prefix = BackupStorePaths.manifestsPrefix(wildcard);

    // then
    assertThat(prefix).isEqualTo("manifests/");
  }
}
