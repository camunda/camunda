/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.test.VersionCompatibilityMatrix.CachedVersionProvider;
import io.camunda.zeebe.test.VersionCompatibilityMatrix.GithubAPI;
import io.camunda.zeebe.test.VersionCompatibilityMatrix.GithubVersionProvider;
import io.camunda.zeebe.test.VersionCompatibilityMatrix.LegacyVersionProvider;
import io.camunda.zeebe.test.VersionCompatibilityMatrix.ReleaseVerifiedGithubVersionProvider;
import io.camunda.zeebe.test.VersionCompatibilityMatrix.VersionCompatibilityConfig;
import io.camunda.zeebe.test.VersionCompatibilityMatrix.VersionInfo;
import io.camunda.zeebe.test.VersionCompatibilityMatrix.VersionProvider;
import io.camunda.zeebe.util.SemanticVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class VersionCompatibilityMatrixTest {

  final VersionCompatibilityMatrix matrix =
      new VersionCompatibilityMatrix(
          () ->
              Stream.of(
                  VersionInfo.of("8.7.0"),
                  VersionInfo.of("8.7.11"),
                  VersionInfo.of("8.7.12"),
                  VersionInfo.of("8.8.0"),
                  VersionInfo.of("8.8.1"),
                  VersionInfo.of("8.8.2")),
          new VersionCompatibilityConfig() {
            @Override
            public SemanticVersion getCurrentVersion() {
              return SemanticVersion.parse("8.8.2").orElseThrow();
            }

            @Override
            public Optional<SemanticVersion> getPreviousMinorVersion() {
              return SemanticVersion.parse("8.7.12");
            }
          });

  @Test
  void testFromPreviousMinorToCurrent() {
    final var paths =
        matrix
            .fromPreviousMinorToCurrent()
            .map(args -> Stream.of(args.get()).map(Object::toString).toList())
            .toList();

    assertThat(paths).containsExactly(List.of("8.7.12", "CURRENT"));
  }

  @Test
  void testFromPreviousPatchesToCurrent() {
    final var paths =
        matrix
            .fromPreviousPatchesToCurrent()
            .map(args -> Stream.of(args.get()).map(Object::toString).toList())
            .toList();

    assertThat(paths)
        .containsExactlyInAnyOrder(
            List.of("8.7.0", "CURRENT"),
            List.of("8.7.11", "CURRENT"),
            List.of("8.7.12", "CURRENT"),
            List.of("8.8.0", "CURRENT"),
            List.of("8.8.1", "CURRENT"));
  }

  @Test
  void testFromFirstAndLastPatchToCurrent() {
    final var paths =
        matrix
            .fromFirstAndLastPatchToCurrent()
            .map(args -> Stream.of(args.get()).map(Object::toString).toList())
            .toList();

    assertThat(paths)
        .containsExactlyInAnyOrder(List.of("8.7.0", "CURRENT"), List.of("8.7.12", "CURRENT"));
  }

  @Test
  void testFull() {
    final var paths =
        matrix.full().map(args -> Stream.of(args.get()).map(Object::toString).toList()).toList();

    assertThat(paths)
        .containsExactlyInAnyOrder(
            List.of("8.7.0", "8.7.11"),
            List.of("8.7.0", "8.7.12"),
            List.of("8.7.0", "8.8.0"),
            List.of("8.7.0", "8.8.1"),
            List.of("8.7.0", "8.8.2"),
            List.of("8.7.11", "8.7.12"),
            List.of("8.7.11", "8.8.0"),
            List.of("8.7.11", "8.8.1"),
            List.of("8.7.11", "8.8.2"),
            List.of("8.7.12", "8.8.0"),
            List.of("8.7.12", "8.8.1"),
            List.of("8.7.12", "8.8.2"),
            List.of("8.8.0", "8.8.1"),
            List.of("8.8.0", "8.8.2"),
            List.of("8.8.1", "8.8.2"));
  }

  @Test
  void shouldIgnoreKnownIncompatibleUpgradeRangeInFullMatrix() {
    // given
    // INCOMPATIBLE_UPGRADES encodes (8.5.17 -> 8.6.13), i.e.:
    //  - from 8.5.[17+] to 8.6.0..8.6.12 is incompatible
    //  - 8.6.13 is the first compatible patch on 8.6
    final var versions =
        Stream.concat(
                // 8.5.16, 8.5.17, 8.5.18
                IntStream.rangeClosed(16, 18).mapToObj(patch -> "8.5." + patch),
                // 8.6.0 .. 8.6.13
                IntStream.rangeClosed(0, 13).mapToObj(patch -> "8.6." + patch))
            .collect(Collectors.toSet());

    final VersionCompatibilityMatrix matrix =
        new VersionCompatibilityMatrix(() -> versions.stream().map(VersionInfo::of));

    // when
    final var upgradePairs =
        matrix
            .full()
            .map(
                args -> {
                  final Object[] values = args.get();
                  return values[0] + "->" + values[1];
                })
            .collect(Collectors.toSet());

    // then

    // 1) Upgrades from a lower patch (8.5.16) to all 8.6.x should be present
    IntStream.rangeClosed(0, 13)
        .forEach(toPatch -> assertThat(upgradePairs).contains("8.5.16->8.6." + toPatch));

    // 2) All upgrades from 8.5.[17,18] to 8.6.0..8.6.12 must be excluded
    IntStream.rangeClosed(17, 18)
        .forEach(
            fromPatch ->
                IntStream.rangeClosed(0, 12)
                    .forEach(
                        toPatch ->
                            assertThat(upgradePairs)
                                .doesNotContain("8.5." + fromPatch + "->8.6." + toPatch)));

    // 3) Upgrades from 8.5.[17,18] to the first compatible 8.6 patch (8.6.13) must be allowed
    IntStream.rangeClosed(17, 18)
        .forEach(fromPatch -> assertThat(upgradePairs).contains("8.5." + fromPatch + "->8.6.13"));
  }

  /**
   * During releases some tags might not be fully published yet and would make the rolling update
   * test fail
   *
   * <p>Therefore, we created a smarter version discovery strategy that checks if tags have been
   * fully released.
   *
   * <p>We are disabling this test by default to avoid flakiness during releases, but it's still
   * helpful for refactoring.
   */
  @Disabled(
      "During releases some tags might not be fully published yet and would make this and the rolling update test fail")
  @Test
  void testCompareLegacyToCurrentVersionDiscovery() {
    final var liveMatrix = VersionCompatibilityMatrix.useUncached();

    final var discoveredVersions = liveMatrix.discoverVersions().map(VersionInfo::version);
    final var legacyDiscoveredVersions =
        new LegacyVersionProvider().discoverVersions().map(VersionInfo::version);

    assertThat(discoveredVersions)
        .containsExactlyInAnyOrderElementsOf(legacyDiscoveredVersions.toList());
  }

  @Nested
  class ShardingTest {
    @ParameterizedTest(name = "Sharding {0} elements into {1} shards")
    @MethodSource("sizeAndShards")
    void shouldShardCompletely(final int size, final int totalShards) {
      final var input = IntStream.range(0, size).boxed().toList();
      final var sharded = new LinkedList<Integer>();
      for (int i = 0; i < totalShards; i++) {
        final var shard = VersionCompatibilityMatrix.shard(input, i, totalShards).toList();
        assertThat(shard).isNotEmpty();
        sharded.addAll(shard);
      }
      assertThat(sharded).isEqualTo(input);
    }

    static Stream<Arguments> sizeAndShards() {
      return IntStream.rangeClosed(0, 10)
          .boxed()
          .flatMap(
              size ->
                  IntStream.rangeClosed(1, 10)
                      .boxed()
                      .filter(shards -> shards < size)
                      .map(shards -> arguments(size, shards)));
    }
  }

  @Nested
  class VersionInfoTest {

    @Test
    void shouldHandleNullVersion() {
      final SemanticVersion semanticVersion = null;
      final var version = VersionInfo.of(semanticVersion);

      assertThat(version).isNull();
    }

    @Test
    void shouldReturnNullForInvalidVersionString() {
      final var version = VersionInfo.of("whatsoever");

      assertThat(version).isNull();
    }

    @Test
    void shouldDetectPreReleaseVersion() {
      final var version = VersionInfo.of("8.8.0-alpha.1");

      assertThat(version.isReleased()).isFalse();
    }

    @Test
    void shouldUpdateLatestForEmptyList() {
      final var versions = VersionInfo.updateLatest(Collections.emptyList());

      assertThat(versions).isEmpty();
    }

    @Test
    void shouldUpdateLatest() {
      final var version8711 = VersionInfo.of("8.7.11");
      final var version8712 = VersionInfo.of("8.7.12");
      final var version880 = VersionInfo.of("8.8.0");

      final var versions = VersionInfo.updateLatest(List.of(version8711, version8712, version880));

      assertThat(versions)
          .containsExactlyInAnyOrder(version8711, version8712.asLatest(), version880.asLatest());
    }
  }

  @Nested
  class GithubVersionProviderTest {

    @Test
    void shouldFilterOutInvalidVersions() {
      final var api = mock(GithubAPI.class);
      final var provider = new GithubVersionProvider(api);

      when(api.fetchTags())
          .thenReturn(Stream.of("refs/tags/", "refs/tags/invalid-123").map(GithubAPI.Ref::new));

      final var versions = provider.discoverVersions();

      assertThat(versions).isEmpty();
    }

    @Test
    void shouldFilterOutPreReleaseVersions() {
      final var api = mock(GithubAPI.class);
      final var provider = new GithubVersionProvider(api);

      when(api.fetchTags())
          .thenReturn(
              Stream.of(
                      "refs/tags/8.7.11",
                      "refs/tags/8.7.12",
                      "refs/tags/8.7.12-optimize",
                      "refs/tags/8.8.0",
                      "refs/tags/8.8.1-alpha")
                  .map(GithubAPI.Ref::new));

      final var versions = provider.discoverVersions().map(info -> info.version().toString());

      assertThat(versions).containsExactlyInAnyOrder("8.7.11", "8.7.12", "8.8.0");
    }

    @Test
    void shouldMarkLatestPerMinor() {
      final var api = mock(GithubAPI.class);
      final var provider = new GithubVersionProvider(api);

      when(api.fetchTags())
          .thenReturn(
              Stream.of("refs/tags/8.7.11", "refs/tags/8.7.12", "refs/tags/8.8.0")
                  .map(GithubAPI.Ref::new));

      final var versions = provider.discoverVersions();

      assertThat(versions)
          .containsExactlyInAnyOrder(
              VersionInfo.of("8.7.11"),
              VersionInfo.of("8.7.12").asLatest(),
              VersionInfo.of("8.8.0").asLatest());
    }
  }

  @Nested
  class AdvancedGithubVersionProviderTest {

    @Test
    void shouldPassThroughReleasedLatestVersions() {
      final var api = mock(GithubAPI.class);
      final var baseProvider = mock(VersionProvider.class);
      final var provider = new ReleaseVerifiedGithubVersionProvider(baseProvider, api);

      final var info = VersionInfo.of("8.8.0").asLatest();
      when(baseProvider.discoverVersions()).thenReturn(Stream.of(info));
      when(api.fetchRelease(info.version()))
          .thenReturn(Optional.of(new GithubAPI.Release(info.version().toString())));

      assertThat(provider.discoverVersions()).isNotEmpty();
    }

    @Test
    void shouldFilterUnReleasedLatestVersions() {
      final var api = mock(GithubAPI.class);
      final var baseProvider = mock(VersionProvider.class);
      final var provider = new ReleaseVerifiedGithubVersionProvider(baseProvider, api);

      final var info = VersionInfo.of("8.8.0").asLatest();
      when(baseProvider.discoverVersions()).thenReturn(Stream.of(info));
      when(api.fetchRelease(info.version())).thenReturn(Optional.empty());

      assertThat(provider.discoverVersions()).isEmpty();
    }

    @Test
    void shouldUpdateLatestToLatestReleasedVersion() {
      final var api = mock(GithubAPI.class);
      final var baseProvider = mock(VersionProvider.class);
      final var provider = new ReleaseVerifiedGithubVersionProvider(baseProvider, api);

      final var releasedVersion = VersionInfo.of("8.7.11");
      final var unreleasedVersion = VersionInfo.of("8.7.12").asLatest();

      when(baseProvider.discoverVersions())
          .thenReturn(Stream.of(releasedVersion, unreleasedVersion));
      when(api.fetchRelease(releasedVersion.version()))
          .thenReturn(Optional.of(new GithubAPI.Release(releasedVersion.version().toString())));
      when(api.fetchRelease(unreleasedVersion.version())).thenReturn(Optional.empty());

      final var discoveredVersions = provider.discoverVersions();

      assertThat(discoveredVersions).containsExactlyInAnyOrder(releasedVersion.asLatest());
    }
  }

  @Nested
  class CachedVersionProviderTest {
    @Test
    void shouldCacheOnDisk(@org.junit.jupiter.api.io.TempDir final Path tempDir) throws Exception {
      final var baseProvider = mock(VersionProvider.class);
      final var cacheFile = tempDir.resolve("camunda-versions.json");
      final var provider = new CachedVersionProvider(baseProvider, cacheFile);

      final var versions = List.of(VersionInfo.of("8.7.9"), VersionInfo.of("8.8.0").asLatest());
      when(baseProvider.discoverVersions()).thenReturn(versions.stream());

      final var versions1 = provider.discoverVersions().toList();
      final var versions2 = provider.discoverVersions().toList();

      assertThat(versions1).containsExactlyInAnyOrderElementsOf(versions);
      assertThat(versions2).containsExactlyInAnyOrderElementsOf(versions);
      verify(baseProvider, times(1)).discoverVersions();

      // Verify cache file exists and has proper contents
      assertThat(cacheFile).exists();
      final var cacheContent = Files.readString(cacheFile);
      assertThat(cacheContent)
          .isEqualTo(
              """
              [ {
                "version" : "8.7.9",
                "isReleased" : true,
                "isLatest" : false
              }, {
                "version" : "8.8.0",
                "isReleased" : true,
                "isLatest" : true
              } ]""");
    }
  }
}
