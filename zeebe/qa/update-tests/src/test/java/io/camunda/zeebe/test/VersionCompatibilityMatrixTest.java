/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
  void testFromPreviousPatchesToCurrentWithTwoMinorGap() {
    // Simulates the case where the dev version (8.10.0-SNAPSHOT) is two minors ahead of the
    // latest released minor (8.8.x) — e.g. when 8.9 was never released.
    final var snapshotMatrix =
        new VersionCompatibilityMatrix(
            () ->
                Stream.of(
                    VersionInfo.of("8.7.0"),
                    VersionInfo.of("8.8.0"),
                    VersionInfo.of("8.8.1"),
                    VersionInfo.of("8.8.2")),
            new VersionCompatibilityConfig() {
              @Override
              public SemanticVersion getCurrentVersion() {
                return SemanticVersion.parse("8.10.0-SNAPSHOT").orElseThrow();
              }

              @Override
              public Optional<SemanticVersion> getPreviousMinorVersion() {
                return SemanticVersion.parse("8.8.2");
              }
            });

    final var paths =
        snapshotMatrix
            .fromPreviousPatchesToCurrent()
            .map(args -> Stream.of(args.get()).map(Object::toString).toList())
            .toList();

    // Should be empty — rolling updates only support consecutive minors (N -> N+1).
    // 8.8 -> 8.10 skips a minor, so cross-minor tests are not valid.
    assertThat(paths).isEmpty();
  }

  @Test
  void testFromPreviousPatchesToCurrentWithTwoMinorGapIncludesIntraMinorPatches() {
    // Simulates the case where the dev version is 8.10.1-SNAPSHOT, 8.9 was skipped,
    // but 8.10.0 has already been released. The method should still return the intra-minor
    // upgrade path 8.10.0 -> CURRENT, while excluding cross-minor paths (8.8.x -> CURRENT).
    final var snapshotMatrix =
        new VersionCompatibilityMatrix(
            () ->
                Stream.of(
                    VersionInfo.of("8.7.0"),
                    VersionInfo.of("8.8.0"),
                    VersionInfo.of("8.8.1"),
                    VersionInfo.of("8.8.2"),
                    VersionInfo.of("8.10.0")),
            new VersionCompatibilityConfig() {
              @Override
              public SemanticVersion getCurrentVersion() {
                return SemanticVersion.parse("8.10.1-SNAPSHOT").orElseThrow();
              }

              @Override
              public Optional<SemanticVersion> getPreviousMinorVersion() {
                return SemanticVersion.parse("8.10.0");
              }
            });

    final var paths =
        snapshotMatrix
            .fromPreviousPatchesToCurrent()
            .map(args -> Stream.of(args.get()).map(Object::toString).toList())
            .toList();

    // Only the intra-minor patch upgrade should be returned
    assertThat(paths).containsExactly(List.of("8.10.0", "CURRENT"));
  }

  @Test
  void testFromFirstAndLastPatchToCurrentWithTwoMinorGap() {
    // Simulates the case where the dev version (8.10.0-SNAPSHOT) is two minors ahead of the
    // latest released minor (8.8.x) — e.g. when 8.9 was never released.
    final var snapshotMatrix =
        new VersionCompatibilityMatrix(
            () ->
                Stream.of(
                    VersionInfo.of("8.7.0"),
                    VersionInfo.of("8.8.0"),
                    VersionInfo.of("8.8.1"),
                    VersionInfo.of("8.8.2")),
            new VersionCompatibilityConfig() {
              @Override
              public SemanticVersion getCurrentVersion() {
                return SemanticVersion.parse("8.10.0-SNAPSHOT").orElseThrow();
              }

              @Override
              public Optional<SemanticVersion> getPreviousMinorVersion() {
                return SemanticVersion.parse("8.8.0");
              }
            });

    // Should be empty -- intended
    assertThat(snapshotMatrix.fromFirstAndLastPatchToCurrent().toList()).isEmpty();
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
  void shouldExcludeVersionsBelowMinimumSupportedFromFullMatrix() {
    // given — mix of versions below and above the minimum supported version (8.6.0)
    final var versions =
        Stream.concat(
                IntStream.rangeClosed(16, 18).mapToObj(patch -> "8.5." + patch),
                IntStream.rangeClosed(0, 3).mapToObj(patch -> "8.6." + patch))
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

    // then — no 8.5.x pairs, only intra-minor 8.6.x pairs
    IntStream.rangeClosed(16, 18)
        .forEach(
            fromPatch -> assertThat(upgradePairs).noneMatch(p -> p.startsWith("8.5." + fromPatch)));

    assertThat(upgradePairs).contains("8.6.0->8.6.1");
    assertThat(upgradePairs).contains("8.6.0->8.6.3");
    assertThat(upgradePairs).contains("8.6.2->8.6.3");
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
  class ShardPendingTest {

    private static final Set<String> METHODS = Set.of("methodA", "methodB", "methodC");

    private static final List<Arguments> COMBINATIONS =
        List.of(
            Arguments.of("8.7.0", "8.7.11"),
            Arguments.of("8.7.11", "8.7.12"),
            Arguments.of("8.7.12", "8.8.0"),
            Arguments.of("8.8.0", "8.8.1"),
            Arguments.of("8.8.1", "8.8.2"));

    @ParameterizedTest(name = "into {0} shards")
    @ValueSource(ints = {1, 2, 3, 5, 7})
    void shouldAssignEveryPendingPairToExactlyOneShard(final int totalShards) {
      // when
      final var shards = shardAll(COMBINATIONS, null, METHODS, totalShards);

      // then
      assertThat(shards.stream().flatMap(List::stream).toList())
          .containsExactlyInAnyOrderElementsOf(pairs(COMBINATIONS));
      final var sizes = shards.stream().map(List::size).toList();
      assertThat(Collections.max(sizes) - Collections.min(sizes)).isLessThanOrEqualTo(1);
    }

    @Test
    void shouldTreatMissingReportAsNothingCached(@TempDir final Path tempDir) {
      // when
      final var shards = shardAll(COMBINATIONS, tempDir.resolve("missing"), METHODS, 2);

      // then
      assertThat(shards.stream().flatMap(List::stream).toList())
          .containsExactlyInAnyOrderElementsOf(pairs(COMBINATIONS));
    }

    @Test
    void shouldOnlyAssignPairsWithMissingInvocations(@TempDir final Path tempDir) throws Exception {
      // given - 8.7.0->8.7.11 is fully cached, 8.7.11->8.7.12 only partially
      final var report = tempDir.resolve("report");
      Files.writeString(
          report,
          """
          methodA,8.7.0->8.7.11
          methodB,8.7.0->8.7.11
          methodC,8.7.0->8.7.11
          methodA,8.7.11->8.7.12
          """);
      final var combinations =
          List.of(Arguments.of("8.7.0", "8.7.11"), Arguments.of("8.7.11", "8.7.12"));

      // when
      final var shards = shardAll(combinations, report, METHODS, 2);

      // then
      assertThat(shards.stream().flatMap(List::stream).toList()).containsExactly("8.7.11->8.7.12");
    }

    @Test
    void shouldAssignNothingWhenAllPairsAreCached(@TempDir final Path tempDir) throws Exception {
      // given
      final var report = tempDir.resolve("report");
      Files.writeString(
          report,
          """
          methodA,8.7.0->8.7.11
          methodB,8.7.0->8.7.11
          """);
      final var combinations = List.of(Arguments.of("8.7.0", "8.7.11"));

      // when
      final var shards = shardAll(combinations, report, Set.of("methodA", "methodB"), 3);

      // then
      assertThat(shards).allSatisfy(shard -> assertThat(shard).isEmpty());
    }

    @Test
    void shouldBalanceShardsByMissingInvocations(@TempDir final Path tempDir) throws Exception {
      // given - 8.7.0->8.7.11 misses all 3 invocations, every other pair misses only 1
      final var report = tempDir.resolve("report");
      Files.writeString(
          report,
          """
          methodA,8.7.11->8.7.12
          methodB,8.7.11->8.7.12
          methodA,8.7.12->8.8.0
          methodB,8.7.12->8.8.0
          methodA,8.8.0->8.8.1
          methodB,8.8.0->8.8.1
          """);
      final var combinations =
          List.of(
              Arguments.of("8.7.0", "8.7.11"),
              Arguments.of("8.7.11", "8.7.12"),
              Arguments.of("8.7.12", "8.8.0"),
              Arguments.of("8.8.0", "8.8.1"));

      // when
      final var shards = shardAll(combinations, report, METHODS, 2);

      // then - a count-based split would put 2 pairs (4 and 2 invocations) on each shard
      assertThat(shards)
          .containsExactlyInAnyOrder(
              List.of("8.7.0->8.7.11"), List.of("8.7.11->8.7.12", "8.7.12->8.8.0", "8.8.0->8.8.1"));
    }

    @Test
    void shouldIgnoreCachedEntriesOfMethodsNoLongerInTheMatrix(@TempDir final Path tempDir)
        throws Exception {
      // given - the pair has as many cached entries as there are methods, but one of them belongs
      // to a method that was since renamed or removed, so methodC was never run for it
      final var report = tempDir.resolve("report");
      Files.writeString(
          report,
          """
          methodA,8.7.0->8.7.11
          methodB,8.7.0->8.7.11
          removedMethod,8.7.0->8.7.11
          """);
      final var combinations = List.of(Arguments.of("8.7.0", "8.7.11"));

      // when
      final var shards = shardAll(combinations, report, METHODS, 2);

      // then
      assertThat(shards.stream().flatMap(List::stream).toList()).containsExactly("8.7.0->8.7.11");
    }

    private static List<List<String>> shardAll(
        final List<Arguments> combinations,
        final Path report,
        final Set<String> expectedMethods,
        final int totalShards) {
      return IntStream.range(0, totalShards)
          .mapToObj(
              index ->
                  pairs(
                      VersionCompatibilityMatrix.shardPending(
                              combinations, report, expectedMethods, index, totalShards)
                          .toList()))
          .toList();
    }

    private static List<String> pairs(final List<Arguments> combinations) {
      return combinations.stream().map(args -> args.get()[0] + "->" + args.get()[1]).toList();
    }
  }

  @Nested
  class VersionMatrixTestMethodsTest {

    @Test
    void shouldReturnOnlyVersionMatrixParameterizedMethods() {
      // when / then — DummyTestClass has exactly 2 @ParameterizedTest methods
      // with @MethodSource("versionMatrix")
      assertThat(VersionCompatibilityMatrix.versionMatrixTestMethods(DummyTestClass.class))
          .containsExactlyInAnyOrder("matchingMethodA", "matchingMethodB");
    }

    @Test
    void shouldReturnNothingForClassWithNoMatchingMethods() {
      // when / then
      assertThat(
              VersionCompatibilityMatrix.versionMatrixTestMethods(
                  VersionCompatibilityMatrixTest.class))
          .isEmpty();
    }

    @SuppressWarnings("unused")
    static class DummyTestClass {
      @ParameterizedTest
      @MethodSource("versionMatrix")
      void matchingMethodA() {}

      @ParameterizedTest
      @MethodSource("versionMatrix")
      void matchingMethodB() {}

      @ParameterizedTest
      @MethodSource("otherSource")
      void nonMatchingParameterized() {}

      @Test
      void plainTest() {}
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

    @Test
    void shouldFilterUnreleasedNonLatestVersion() {
      final var api = mock(GithubAPI.class);
      final var baseProvider = mock(VersionProvider.class);
      final var provider = new ReleaseVerifiedGithubVersionProvider(baseProvider, api);

      // a tag that was pushed but whose release got skipped/aborted, and a later, actually
      // released tag on the same minor
      final var unreleasedVersion = VersionInfo.of("8.7.40");
      final var releasedVersion = VersionInfo.of("8.7.41").asLatest();

      when(baseProvider.discoverVersions())
          .thenReturn(Stream.of(unreleasedVersion, releasedVersion));
      when(api.fetchRelease(unreleasedVersion.version())).thenReturn(Optional.empty());
      when(api.fetchRelease(releasedVersion.version()))
          .thenReturn(Optional.of(new GithubAPI.Release(releasedVersion.version().toString())));

      final var discoveredVersions = provider.discoverVersions();

      verify(api).fetchRelease(unreleasedVersion.version());
      assertThat(discoveredVersions).containsExactlyInAnyOrder(releasedVersion);
    }
  }

  @Nested
  class IncompatibleUpgradesTest {

    @Test
    void shouldRejectUpgradeFromAffectedPatchToIncompatibleTarget() {
      // INCOMPATIBLE_UPGRADES contains (8.5.17, 8.6.13):
      //   from 8.5.[17+] to 8.6.[0..12] is incompatible
      final var from = SemanticVersion.parse("8.5.17").orElseThrow();
      final var to = SemanticVersion.parse("8.6.12").orElseThrow();

      assertThat(VersionCompatibilityMatrix.isCompatible(from, to)).isFalse();
    }

    @Test
    void shouldRejectUpgradeFromLaterPatchToIncompatibleTarget() {
      final var from = SemanticVersion.parse("8.5.18").orElseThrow();
      final var to = SemanticVersion.parse("8.6.0").orElseThrow();

      assertThat(VersionCompatibilityMatrix.isCompatible(from, to)).isFalse();
    }

    @Test
    void shouldAllowUpgradeFromAffectedPatchToFirstCompatibleTarget() {
      // 8.6.13 is the first compatible target
      final var from = SemanticVersion.parse("8.5.17").orElseThrow();
      final var to = SemanticVersion.parse("8.6.13").orElseThrow();

      assertThat(VersionCompatibilityMatrix.isCompatible(from, to)).isTrue();
    }

    @Test
    void shouldAllowUpgradeFromPatchBelowAffectedRange() {
      // 8.5.16 is below the affected range (starts at 8.5.17)
      final var from = SemanticVersion.parse("8.5.16").orElseThrow();
      final var to = SemanticVersion.parse("8.6.0").orElseThrow();

      assertThat(VersionCompatibilityMatrix.isCompatible(from, to)).isTrue();
    }

    @Test
    void shouldAllowUpgradeOnUnrelatedMinors() {
      // 8.7.x -> 8.8.x is not in any incompatible range
      final var from = SemanticVersion.parse("8.7.0").orElseThrow();
      final var to = SemanticVersion.parse("8.8.0").orElseThrow();

      assertThat(VersionCompatibilityMatrix.isCompatible(from, to)).isTrue();
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
