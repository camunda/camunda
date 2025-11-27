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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides combinations of versions that should be compatible with each other. This matrix is used
 * in {@link RollingUpdateTest}.
 */
@SuppressWarnings("unused")
final class VersionCompatibilityMatrix {

  private static final Logger LOG = LoggerFactory.getLogger(VersionCompatibilityMatrix.class);

  private final VersionProvider versionProvider;

  public VersionCompatibilityMatrix() {
    versionProvider = new GithubVersionProvider();
  }

  public VersionCompatibilityMatrix(final VersionProvider versionProvider) {
    this.versionProvider = versionProvider;
  }

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
  static Stream<Arguments> auto() {
    final var matrix = new VersionCompatibilityMatrix();

    if (System.getenv("ZEEBE_CI_CHECK_VERSION_COMPATIBILITY") != null) {
      return matrix.full();
    } else if (System.getenv("ZEEBE_CI_CHECK_CURRENT_VERSION_COMPATIBILITY") != null) {
      return matrix.fromPreviousPatchesToCurrent();
    } else if (System.getenv("CI") != null) {
      return matrix.fromFirstAndLastPatchToCurrent();
    } else {
      return matrix.fromPreviousMinorToCurrent();
    }
  }

  public Stream<Arguments> fromPreviousMinorToCurrent() {
    return Stream.of(Arguments.of(VersionUtil.getPreviousVersion(), "CURRENT"));
  }

  public Stream<Arguments> fromPreviousPatchesToCurrent() {
    final var current = VersionUtil.getSemanticVersion().orElseThrow();
    return discoverVersions()
        .filter(version -> version.compareTo(current) < 0)
        .filter(version -> current.minor() - version.minor() <= 1)
        .map(version -> Arguments.of(version.toString(), "CURRENT"));
  }

  public Stream<Arguments> fromFirstAndLastPatchToCurrent() {
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

  public Stream<Arguments> full() {
    final var versions = discoverVersions().sorted().toList();
    final var latestVersionPerMinor = getLatestVersionPerMinor(versions);
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

  private Map<Integer, SemanticVersion> getLatestVersionPerMinor(
      final List<SemanticVersion> versions) {
    return versions.stream()
        .collect(
            Collectors.toMap(
                SemanticVersion::minor,
                Function.identity(),
                BinaryOperator.maxBy(Comparator.comparing(SemanticVersion::patch))));
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
   * <p>For the latest patch version of each minor version, verifies that the version has been
   * published as a GitHub release before including it. This ensures that only versions with
   * published artifacts are included in the compatibility matrix.
   *
   * @see <a
   *     href="https://docs.github.com/en/rest/git/refs?apiVersion=2022-11-28#list-matching-references--parameters">GitHub
   *     API</a>
   */
  public Stream<SemanticVersion> discoverVersions() {
    final var versions =
        versionProvider
            .discoverVersions()
            .filter(version -> version != null && version.preRelease() == null)
            .toList();

    // Filter out unreleased versions which might not have the right artifacts like maven/docker
    // published yet. The GitHub API for fetching releases is inflexible, thus we optimize to only
    // check for the latest versions of each minor.
    final var unreleasedVersions =
        getLatestVersionPerMinor(versions).values().stream()
            .filter(version -> !versionProvider.isReleased(version))
            .peek(
                version ->
                    LOG.warn(
                        "Latest patch {} for minor version 8.{} has no corresponding GitHub release yet. Excluding from compatibility matrix.",
                        version,
                        version.minor()))
            .collect(Collectors.toSet());

    return versions.stream().filter(version -> !unreleasedVersions.contains(version));
  }

  static class GithubVersionProvider implements VersionProvider {

    final Retry retry =
        Retry.of(
            "github-api",
            RetryConfig.custom()
                .maxAttempts(10)
                .intervalFunction(IntervalFunction.ofExponentialBackoff())
                .build());

    @Override
    public Stream<SemanticVersion> discoverVersions() {
      return fetchTags().map(Ref::toSemanticVersion);
    }

    @Override
    public boolean isReleased(final SemanticVersion version) {
      return fetchRelease(version).isPresent();
    }

    private Optional<Release> fetchRelease(final SemanticVersion tagName) {
      final var endpoint =
          URI.create(
              "https://api.github.com/repos/camunda/camunda/releases/tags/" + tagName.toString());
      try (final var httpClient = HttpClient.newHttpClient()) {
        final var response =
            retry.executeCallable(
                () ->
                    httpClient.send(
                        HttpRequest.newBuilder().GET().uri(endpoint).build(),
                        BodyHandlers.ofByteArray()));

        final var statusCode = response.statusCode();
        if (statusCode == 404) {
          return Optional.empty();
        }

        if (statusCode < 200 || statusCode >= 300) {
          final var body = new String(Objects.requireNonNullElse(response.body(), new byte[0]));
          throw new IOException(
              String.format(
                  "Failed to fetch release from GitHub API. Status: %d, Endpoint: %s, Response: %s",
                  statusCode, endpoint, body.isEmpty() ? "<empty>" : body));
        }

        return Optional.ofNullable(new ObjectMapper().readValue(response.body(), Release.class));
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    private Stream<Ref> fetchTags() {
      final var endpoint =
          URI.create("https://api.github.com/repos/camunda/camunda/git/matching-refs/tags/8.");
      try (final var httpClient = HttpClient.newHttpClient()) {
        final var response =
            retry.executeCallable(
                () ->
                    httpClient.send(
                        HttpRequest.newBuilder().GET().uri(endpoint).build(),
                        BodyHandlers.ofByteArray()));

        final var statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
          final var body = new String(Objects.requireNonNullElse(response.body(), new byte[0]));
          throw new IOException(
              String.format(
                  "Failed to fetch tags from GitHub API. Status: %d, Endpoint: %s, Response: %s",
                  statusCode, endpoint, body.isEmpty() ? "<empty>" : body));
        }

        final var refs = new ObjectMapper().readValue(response.body(), Ref[].class);
        return Stream.of(refs);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ref(String ref) {
      SemanticVersion toSemanticVersion() {
        return SemanticVersion.parse(ref.substring("refs/tags/".length())).orElse(null);
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Release(String tag_name, boolean prerelease) {
      SemanticVersion toSemanticVersion() {
        return SemanticVersion.parse(tag_name).orElse(null);
      }
    }
  }

  interface VersionProvider {
    Stream<SemanticVersion> discoverVersions();

    boolean isReleased(SemanticVersion version);
  }
}
