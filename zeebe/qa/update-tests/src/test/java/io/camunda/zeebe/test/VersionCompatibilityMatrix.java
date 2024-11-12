/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.StreamUtil;
import io.camunda.zeebe.util.VersionUtil;
import io.camunda.zeebe.util.VisibleForTesting;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Comparator;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Provides combinations of versions that should be compatible with each other. This matrix is used
 * in {@link RollingUpdateTest}.
 */
@SuppressWarnings("unused")
final class VersionCompatibilityMatrix {

  /**
   * Automatically chooses the matrix to use depending on the environment where tests are run.
   *
   * <ul>
   *   <li>Locally: {@link #fromPreviousMinorToCurrent()} for fast feedback.
   *   <li>CI: {@link #fromFirstAndLastPatchToCurrent()} for extended coverage without taking too
   *       much time.
   *   <li>Periodic tests for current versions: {@link #fromPreviousPatchesToCurrent()} to ensure
   *       that the current version is compatible with all released patches.
   *   <li>Periodic tests for released versions: {@link #full()} for full coverage of all allowed
   *       upgrade paths.
   * </ul>
   */
  private static Stream<Arguments> auto() {
    if (System.getenv("ZEEBE_CI_CHECK_VERSION_COMPATIBILITY") != null) {
      return full();
    } else if (System.getenv("ZEEBE_CI_CHECK_CURRENT_VERSION_COMPATIBILITY") != null) {
      return fromPreviousPatchesToCurrent();
    } else if (System.getenv("CI") != null) {
      return fromFirstAndLastPatchToCurrent();
    } else {
      return fromPreviousMinorToCurrent();
    }
  }

  private static Stream<Arguments> fromPreviousMinorToCurrent() {
    return Stream.of(Arguments.of(VersionUtil.getPreviousVersion(), "CURRENT"));
  }

  private static Stream<Arguments> fromPreviousPatchesToCurrent() {
    final var current = VersionUtil.getSemanticVersion().orElseThrow();
    return discoverVersions()
        .filter(version -> version.compareTo(current) < 0)
        .filter(version -> current.minor() - version.minor() <= 1)
        .map(version -> Arguments.of(version.toString(), "CURRENT"));
  }

  private static Stream<Arguments> fromFirstAndLastPatchToCurrent() {
    final var current = VersionUtil.getSemanticVersion().orElseThrow();

    final var minAndMaxPatch =
        discoverVersions()
            .filter(version -> version.compareTo(current) < 0)
            .filter(version -> current.minor() - version.minor() == 1)
            .collect(StreamUtil.minMax(Comparator.comparing(SemanticVersion::patch)));
    return Stream.of(
        Arguments.of(minAndMaxPatch.min().toString(), "CURRENT"),
        Arguments.of(minAndMaxPatch.max().toString(), "CURRENT"));
  }

  private static Stream<Arguments> full() {
    final var versions = discoverVersions().sorted().toList();
    final var latestVersionPerMinor =
        versions.stream()
            .collect(
                Collectors.toMap(
                    SemanticVersion::minor,
                    Function.identity(),
                    BinaryOperator.maxBy(Comparator.comparing(SemanticVersion::patch))));
    final var combinations =
        versions.stream()
            .filter(version -> version.minor() > 0)
            .flatMap(
                version1 ->
                    versions.stream()
                        .filter(version2 -> version1.compareTo(version2) < 0)
                        .filter(version2 -> version2.minor() - version1.minor() <= 1)
                        .filter(
                            version2 -> {
                              if (version1.minor() <= 4 && version2.minor() > version1.minor()) {
                                // When updating from 8.4 (or earlier) to the next minor, only test
                                // the latest patch.
                                // Only 8.5 and onwards allow updating to a not-latest patch.
                                return latestVersionPerMinor.get(version2.minor()).equals(version2);
                              } else {
                                return true;
                              }
                            })
                        .map(version2 -> Arguments.of(version1.toString(), version2.toString())))
            .toList();

    final var index =
        Optional.ofNullable(System.getenv("ZEEBE_CI_CHECK_VERSION_COMPATIBILITY_INDEX"))
            .map(Integer::parseInt)
            .orElse(0);
    final var total =
        Optional.ofNullable(System.getenv("ZEEBE_CI_CHECK_VERSION_COMPATIBILITY_TOTAL"))
            .map(Integer::parseInt)
            .orElse(1);
    return shard(combinations, index, total);
  }

  @VisibleForTesting
  static <T> Stream<T> shard(final SequencedCollection<T> list, final int index, final int total) {
    if (list.size() < total) {
      throw new IllegalArgumentException(
          "Can't shard a list of size %d into %d shards".formatted(list.size(), total));
    }
    final var shardSize = Math.floorDiv(list.size(), total);
    final var shardStart = index * shardSize;
    // The last shard includes the remaining elements. At max, it will have `total` more elements
    // than a regular shard.
    final var shardLimit = index == total - 1 ? shardSize + total : shardSize;
    return list.stream().skip(shardStart).limit(shardLimit);
  }

  /**
   * Discovers Zeebe versions that aren't pre-releases. Sourced from the GitHub API and can fail on
   * network issues. Includes all versions since 8.0.
   *
   * @see <a
   *     href="https://docs.github.com/en/rest/git/refs?apiVersion=2022-11-28#list-matching-references--parameters">GitHub
   *     API</a>
   */
  static Stream<SemanticVersion> discoverVersions() {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ref(String ref) {
      SemanticVersion toSemanticVersion() {
        return SemanticVersion.parse(ref.substring("refs/tags/".length())).orElse(null);
      }
    }
    final var endpoint =
        URI.create("https://api.github.com/repos/camunda/camunda/git/matching-refs/tags/8.");
    try (final var httpClient = HttpClient.newHttpClient()) {
      final var retry =
          Retry.of(
              "github-api",
              RetryConfig.custom()
                  .maxAttempts(10)
                  .intervalFunction(IntervalFunction.ofExponentialBackoff())
                  .build());
      final var response =
          retry.executeCallable(
              () ->
                  httpClient.send(
                      HttpRequest.newBuilder().GET().uri(endpoint).build(),
                      BodyHandlers.ofByteArray()));
      final var refs = new ObjectMapper().readValue(response.body(), Ref[].class);
      return Stream.of(refs)
          .map(Ref::toSemanticVersion)
          .filter(version -> version != null && version.preRelease() == null);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
