/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.schema.ExporterMigrationTestHelper.DockerHubTag;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExporterMigrationTestHelperTest {

  @Test
  void shouldReturnEmptyWhenOnlyGaAndSnapshotTagsPresent() {
    // given
    final List<DockerHubTag> tags =
        List.of(
            new DockerHubTag("8.8.0", "2026-01-01T00:00:00Z"),
            new DockerHubTag("8.8-SNAPSHOT", "2026-01-02T00:00:00Z"));

    // when
    final var latestPreRelease = ExporterMigrationTestHelper.findLatestPreRelease(tags, "8.8");

    // then
    assertThat(latestPreRelease).isEmpty();
  }

  @Test
  void shouldExcludeGaTagsEvenIfMoreRecentlyPushed() {
    // given
    final List<DockerHubTag> tags =
        List.of(
            new DockerHubTag("8.8.0", "2026-05-01T00:00:00Z"),
            new DockerHubTag("8.8.0-alpha1", "2026-01-01T00:00:00Z"));

    // when
    final var latestPreRelease = ExporterMigrationTestHelper.findLatestPreRelease(tags, "8.8");

    // then
    assertThat(latestPreRelease).contains("8.8.0-alpha1");
  }

  @Test
  void shouldPickLatestPlainAlphaTagWhenNoRcExistsYet() {
    // given
    // a brand-new alpha stage may not have an rc build yet.
    final List<DockerHubTag> tags =
        List.of(
            new DockerHubTag("8.8.0-alpha1", "2026-01-01T00:00:00Z"),
            new DockerHubTag("8.8.0-alpha2", "2026-02-01T00:00:00Z"));

    // when
    final var latestPreRelease = ExporterMigrationTestHelper.findLatestPreRelease(tags, "8.8");

    // then
    assertThat(latestPreRelease).contains("8.8.0-alpha2");
  }

  @Test
  void shouldIgnoreNonPreReleaseVariantTagsEvenIfMoreRecentlyPushed() {
    // given
    // a hypothetical variant/build tag sharing the minor prefix that is neither GA, snapshot, nor
    // a real alpha/rc stage must never be picked over a genuine pre-release tag.
    final List<DockerHubTag> tags =
        List.of(
            new DockerHubTag("8.8.0-ubi", "2026-06-01T00:00:00Z"),
            new DockerHubTag("8.8.0-alpha1", "2026-01-01T00:00:00Z"));

    // when
    final var latestPreRelease = ExporterMigrationTestHelper.findLatestPreRelease(tags, "8.8");

    // then
    assertThat(latestPreRelease).contains("8.8.0-alpha1");
  }

  @Test
  void shouldIgnoreBareFloatingMinorTag() {
    // given
    final List<DockerHubTag> tags =
        List.of(
            new DockerHubTag("8.8", "2026-06-01T00:00:00Z"),
            new DockerHubTag("8.8.0-alpha1", "2026-01-01T00:00:00Z"));

    // when
    final var latestPreRelease = ExporterMigrationTestHelper.findLatestPreRelease(tags, "8.8");

    // then
    assertThat(latestPreRelease).contains("8.8.0-alpha1");
  }

  @Test
  void shouldMatchAlphaTagWithSubPatchSuffix() {
    // given
    // Docker Hub publishes sub-patch alpha rebuilds like "8.8.0-alpha4.1".
    final List<DockerHubTag> tags =
        List.of(new DockerHubTag("8.8.0-alpha4.1", "2026-01-01T00:00:00Z"));

    // when
    final var latestPreRelease = ExporterMigrationTestHelper.findLatestPreRelease(tags, "8.8");

    // then
    assertThat(latestPreRelease).contains("8.8.0-alpha4.1");
  }

  @Test
  void shouldPickMostRecentlyPushedPreReleaseRegardlessOfAlphaStage() {
    // given
    final List<DockerHubTag> tags =
        List.of(
            new DockerHubTag("8.8.0-rc1", "2026-04-02T12:53:39Z"),
            new DockerHubTag("8.8.0-alpha5-rc4", "2026-03-05T12:57:48Z"));

    // when
    final var latestPreRelease = ExporterMigrationTestHelper.findLatestPreRelease(tags, "8.8");

    // then
    assertThat(latestPreRelease).contains("8.8.0-rc1");
  }

  @Test
  void shouldPickTagWithLatestPushTimestampNotHighestRcNumber() {
    // given
    // "8.8.0-rc10" has the higher rc number, but "8.8.0-rc9" was pushed later — only a
    // timestamp-based comparison picks the actually latest tag.
    final List<DockerHubTag> tags =
        List.of(
            new DockerHubTag("8.8.0-rc10", "2026-01-01T00:00:00Z"),
            new DockerHubTag("8.8.0-rc9", "2026-04-01T00:00:00Z"));

    // when
    final var latestPreRelease = ExporterMigrationTestHelper.findLatestPreRelease(tags, "8.8");

    // then
    assertThat(latestPreRelease).contains("8.8.0-rc9");
  }

  @Test
  void shouldIgnoreTagsFromDifferentMinor() {
    // given
    final List<DockerHubTag> tags =
        List.of(
            new DockerHubTag("8.7.0-rc1", "2026-05-01T00:00:00Z"),
            new DockerHubTag("8.8.0-rc1", "2026-01-01T00:00:00Z"));

    // when
    final var latestPreRelease = ExporterMigrationTestHelper.findLatestPreRelease(tags, "8.8");

    // then
    assertThat(latestPreRelease).contains("8.8.0-rc1");
  }

  @Test
  void shouldIgnoreTagsSharingOnlyANumericPrefixWithTheMinor() {
    // given
    // "8.80.0-rc1" starts with "8.8" but belongs to a different minor entirely.
    final List<DockerHubTag> tags =
        List.of(
            new DockerHubTag("8.80.0-rc1", "2026-05-01T00:00:00Z"),
            new DockerHubTag("8.8.0-rc1", "2026-01-01T00:00:00Z"));

    // when
    final var latestPreRelease = ExporterMigrationTestHelper.findLatestPreRelease(tags, "8.8");

    // then
    assertThat(latestPreRelease).contains("8.8.0-rc1");
  }

  @Test
  void shouldIgnoreTagsWithMissingPushTimestamp() {
    // given
    final List<DockerHubTag> tags =
        List.of(
            new DockerHubTag("8.8.0-rc1", null),
            new DockerHubTag("8.8.0-rc2", "2026-01-01T00:00:00Z"));

    // when
    final var latestPreRelease = ExporterMigrationTestHelper.findLatestPreRelease(tags, "8.8");

    // then
    assertThat(latestPreRelease).contains("8.8.0-rc2");
  }
}
