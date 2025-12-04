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
import io.camunda.zeebe.test.VersionCompatibilityMatrix.GithubAPI.Ref;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  /**
   * Explicitly known incompatible upgrade paths which must be excluded from the compatibility
   * matrix.
   *
   * <p>Each entry encodes a range:
   *
   * <ul>
   *   <li><code>from</code>: the first source patch on a given minor that introduces an
   *       incompatibility (all later patches on that minor are also affected)
   *   <li><code>to</code>: the <strong>first compatible</strong> target patch on the next minor
   *       (all earlier patches on that minor are incompatible)
   * </ul>
   *
   * For example, an entry <code>(8.5.17, 8.6.13)</code> means all upgrades from <code>8.5.[17+]
   * </code> to <code>8.6.0</code> through <code>8.6.12</code> are incompatible.
   */
  private static final List<UpgradePath> INCOMPATIBLE_UPGRADES =
      List.of(
          // https://camunda.slack.com/archives/C013MEVQ4M9/p1763733819656189?thread_ts=1763726918.300319&cid=C013MEVQ4M9
          new UpgradePath(parseVersion("8.5.17"), parseVersion("8.6.13"))
          // add further incompatible combinations here if needed
          );

  private final VersionProvider versionProvider;
  private VersionCompatibilityConfig config =
      new VersionCompatibilityConfig() {
        @Override
        public SemanticVersion getCurrentVersion() {
          return VersionUtil.getSemanticVersion().orElseThrow();
        }

        @Override
        public Optional<SemanticVersion> getPreviousMinorVersion() {
          return VersionUtil.getPreviousSemanticVersion();
        }
      };

  public VersionCompatibilityMatrix(final VersionProvider versionProvider) {
    this.versionProvider = versionProvider;
  }

  public VersionCompatibilityMatrix(
      final VersionProvider versionProvider, final VersionCompatibilityConfig config) {
    this.versionProvider = versionProvider;
    this.config = config;
  }

  public static VersionCompatibilityMatrix useCached() {
    final var api = new GithubAPI();
    return new VersionCompatibilityMatrix(
        new CachedVersionProvider(
            new ReleaseVerifiedGithubVersionProvider(new GithubVersionProvider(api), api)));
  }

  public static VersionCompatibilityMatrix useUncached() {
    final var api = new GithubAPI();
    return new VersionCompatibilityMatrix(
        new ReleaseVerifiedGithubVersionProvider(new GithubVersionProvider(api), api));
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
    if (System.getenv("ZEEBE_CI_CHECK_VERSION_COMPATIBILITY") != null) {
      return useCached().full();
    } else if (System.getenv("ZEEBE_CI_CHECK_CURRENT_VERSION_COMPATIBILITY") != null) {
      return useCached().fromPreviousPatchesToCurrent();
    } else if (System.getenv("CI") != null) {
      return useCached().fromFirstAndLastPatchToCurrent();
    } else {
      return useUncached().fromPreviousMinorToCurrent();
    }
  }

  public Stream<Arguments> fromPreviousMinorToCurrent() {
    final var current = config.getCurrentVersion();
    final var previous =
        config
            .getPreviousMinorVersion()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Failed to parse previous version " + VersionUtil.getPreviousVersion()));

    if (!isCompatible(previous, current)) {
      LOG.info(
          "Skipping known incompatible upgrade path from {} to {} in local version matrix.",
          previous,
          current);
      return Stream.empty();
    }

    return Stream.of(Arguments.of(previous.toString(), "CURRENT"));
  }

  public Stream<Arguments> fromPreviousPatchesToCurrent() {
    final var current = config.getCurrentVersion();
    return discoverVersions()
        .map(VersionInfo::version)
        .filter(version -> version.compareTo(current) < 0)
        .filter(version -> current.minor() - version.minor() <= 1)
        .filter(version -> isCompatible(version, current))
        .map(version -> Arguments.of(version.toString(), "CURRENT"));
  }

  public Stream<Arguments> fromFirstAndLastPatchToCurrent() {
    final var current = config.getCurrentVersion();

    final var minAndMaxPatch =
        discoverVersions()
            .map(VersionInfo::version)
            .filter(version -> version.compareTo(current) < 0)
            .filter(version -> current.minor() - version.minor() == 1)
            .filter(next -> isCompatible(current, next))
            .collect(StreamUtil.minMax(Comparator.comparing(SemanticVersion::patch)));

    return Stream.of(
        Arguments.of(minAndMaxPatch.min().toString(), "CURRENT"),
        Arguments.of(minAndMaxPatch.max().toString(), "CURRENT"));
  }

  public Stream<Arguments> full() {
    final var versionInfos = discoverVersions().sorted().toList();
    final var combinations =
        versionInfos.stream()
            .filter(info -> info.version().minor() > 0)
            .flatMap(
                info1 ->
                    versionInfos.stream()
                        .filter(info2 -> info1.compareTo(info2) < 0)
                        .filter(info2 -> info2.version().minor() - info1.version().minor() <= 1)
                        .filter(
                            info2 -> {
                              if (info1.version().minor() <= 4
                                  && info2.version().minor() > info1.version().minor()) {
                                // When updating from 8.4 (or earlier) to the next minor, only test
                                // the latest patch.
                                // Only 8.5 and onwards allow updating to a not-latest patch.
                                return info2.isLatest();
                              } else {
                                return true;
                              }
                            })
                        .filter(info2 -> isCompatible(info1.version(), info2.version()))
                        .map(
                            info2 ->
                                Arguments.of(
                                    info1.version().toString(), info2.version().toString())))
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
   * Discovers Zeebe versions that aren't pre-releases and are released. Sourced from the GitHub API
   * and can fail on network issues. Includes all versions since 8.0.
   *
   * <p>For the latest patch version of each minor version, verifies that the version has been
   * published as a GitHub release before including it. This ensures that only versions with
   * published artifacts are included in the compatibility matrix.
   *
   * @see <a
   *     href="https://docs.github.com/en/rest/git/refs?apiVersion=2022-11-28#list-matching-references--parameters">GitHub
   *     API</a>
   */
  public Stream<VersionInfo> discoverVersions() {
    return versionProvider.discoverVersions();
  }

  private static SemanticVersion parseVersion(final String version) {
    return SemanticVersion.parse(version)
        .orElseThrow(
            () -> new IllegalArgumentException("Invalid semantic version string: " + version));
  }

  private static boolean isCompatible(final SemanticVersion from, final SemanticVersion to) {
    // Compatible if no incompatible range matches
    return INCOMPATIBLE_UPGRADES.stream()
        .noneMatch(
            path -> {
              final var lowerBoundFrom = path.from();
              final var firstCompatibleTo = path.to();

              // Source side: same major/minor as lowerBoundFrom, and patch >= lowerBoundFrom.patch
              if (from.major() != lowerBoundFrom.major()
                  || from.minor() != lowerBoundFrom.minor()
                  || from.patch() < lowerBoundFrom.patch()) {
                return false;
              }

              // Target side: same major/minor as firstCompatibleTo, and patch <
              // firstCompatibleTo.patch
              if (to.major() != firstCompatibleTo.major()
                  || to.minor() != firstCompatibleTo.minor()
                  || to.patch() >= firstCompatibleTo.patch()) {
                return false;
              }

              // From >= lowerBoundFrom AND to < firstCompatibleTo => incompatible
              return true;
            });
  }

  static class CachedVersionProvider implements VersionProvider {
    private static final Path CACHE_FILE = getCacheFilePath();

    private final VersionProvider delegate;
    private final Path cacheFile;

    public CachedVersionProvider(final VersionProvider delegate) {
      this(delegate, CACHE_FILE);
    }

    public CachedVersionProvider(final VersionProvider delegate, final Path cacheFile) {
      this.delegate = delegate;
      this.cacheFile = cacheFile;
    }

    private static Path getCacheFilePath() {
      final var workspace = System.getenv("GITHUB_WORKSPACE");
      final var mavenMultiModule = System.getProperty("maven.multiModuleProjectDirectory");
      final var userDir = System.getProperty("user.dir");

      final var baseDir =
          Objects.requireNonNullElse(
              workspace, Objects.requireNonNullElse(mavenMultiModule, userDir));

      LOG.info("Cache base directory resolution:");
      LOG.info("  GITHUB_WORKSPACE = {}", workspace);
      LOG.info("  maven.multiModuleProjectDirectory = {}", mavenMultiModule);
      LOG.info("  user.dir = {}", userDir);

      return Paths.get(baseDir, ".cache/camunda", "camunda-versions.json");
    }

    @Override
    public Stream<VersionInfo> discoverVersions() {
      LOG.debug("Cache file location: {}", cacheFile.toAbsolutePath());

      // Try to load from cache first
      final var cachedVersions = loadFromCache();
      if (cachedVersions.isPresent()) {
        LOG.info("Loaded {} versions from cache file: {}", cachedVersions.get().size(), cacheFile);
        return cachedVersions.get().stream();
      }

      // Delegate to underlying provider and cache the results
      LOG.info("Cache file not found, fetching versions from delegate provider");
      final var versionInfos = delegate.discoverVersions().toList();
      saveToCache(versionInfos);
      return versionInfos.stream();
    }

    private Optional<List<VersionInfo>> loadFromCache() {
      try {
        if (!Files.exists(cacheFile)) {
          return Optional.empty();
        }

        final var json = Files.readString(cacheFile);
        final var mapper = new ObjectMapper();
        final var cachedInfos = mapper.readValue(json, CachedVersionInfo[].class);
        final var versionInfos =
            Stream.of(cachedInfos)
                .map(
                    cached ->
                        SemanticVersion.parse(cached.version())
                            .map(v -> new VersionInfo(v, cached.isReleased(), cached.isLatest()))
                            .orElse(null))
                .filter(Objects::nonNull)
                .toList();
        return Optional.of(versionInfos);
      } catch (final Exception e) {
        LOG.warn("Failed to load versions from cache file: {}", cacheFile, e);
        return Optional.empty();
      }
    }

    private void saveToCache(final List<VersionInfo> versionInfos) {
      try {
        Files.createDirectories(cacheFile.getParent());
        final var cachedInfos =
            versionInfos.stream()
                .map(
                    info ->
                        new CachedVersionInfo(
                            info.version().toString(), info.isReleased(), info.isLatest()))
                .toList();
        final var mapper = new ObjectMapper();
        final var json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cachedInfos);
        Files.writeString(cacheFile, json);
        LOG.info("Saved {} versions to cache file: {}", versionInfos.size(), cacheFile);
      } catch (final Exception e) {
        LOG.warn("Failed to save versions to cache file: {}", cacheFile, e);
      }
    }

    // Helper record for JSON serialization (Jackson doesn't handle SemanticVersion directly)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CachedVersionInfo(String version, boolean isReleased, boolean isLatest) {}
  }

  static class ReleaseVerifiedGithubVersionProvider implements VersionProvider {

    private final VersionProvider delegate;
    private final GithubAPI api;

    public ReleaseVerifiedGithubVersionProvider(
        final VersionProvider delegate, final GithubAPI api) {
      this.delegate = delegate;
      this.api = api;
    }

    @Override
    public Stream<VersionInfo> discoverVersions() {
      // Mark latest versions, enrich with release status, and filter to released only
      final var releasedVersions =
          delegate
              .discoverVersions()
              .map(
                  info -> {
                    if (info.isLatest()) {
                      final var isReleased = api.fetchRelease(info.version()).isPresent();
                      return new VersionInfo(info.version(), isReleased, info.isLatest());
                    } else {
                      return info;
                    }
                  })
              .peek(
                  info -> {
                    if (!info.isReleased()) {
                      LOG.warn(
                          "{} has no corresponding GitHub release yet. Using previous patch as latest for minor {}.",
                          info.version(),
                          info.version().minor());
                    }
                  })
              .filter(VersionInfo::isReleased)
              .toList();

      // Re-mark latest based on released versions only
      return VersionInfo.updateLatest(releasedVersions).stream();
    }
  }

  static class GithubVersionProvider implements VersionProvider {

    private final GithubAPI api;

    public GithubVersionProvider(final GithubAPI api) {
      this.api = api;
    }

    @Override
    public Stream<VersionInfo> discoverVersions() {
      final var versions =
          api.fetchTags()
              .map(Ref::toSemanticVersion)
              .map(VersionInfo::of)
              .filter(Objects::nonNull) // Filter out null versions
              .filter(VersionInfo::isReleased)
              .toList();

      return VersionInfo.updateLatest(versions).stream();
    }
  }

  @Deprecated
  static class LegacyVersionProvider implements VersionProvider {

    @Override
    public Stream<VersionInfo> discoverVersions() {
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
            .filter(version -> version != null && version.preRelease() == null)
            .map(VersionInfo::of);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  record VersionInfo(SemanticVersion version, boolean isReleased, boolean isLatest)
      implements Comparable<VersionInfo> {

    private static Map<Integer, VersionInfo> findLatestPatchPerMinor(
        final List<VersionInfo> versions) {
      return versions.stream()
          .collect(
              Collectors.toMap(
                  VersionInfo::minor,
                  Function.identity(),
                  BinaryOperator.maxBy(Comparator.comparing(VersionInfo::patch))));
    }

    static List<VersionInfo> updateLatest(final List<VersionInfo> versions) {
      final var latestPerMinor = VersionInfo.findLatestPatchPerMinor(versions);

      return versions.stream()
          .map(
              info -> {
                final var isLatest =
                    info.version().equals(latestPerMinor.get(info.version().minor()).version());
                return isLatest ? info.asLatest() : info.asNotLatest();
              })
          .toList();
    }

    public int minor() {
      return version().minor();
    }

    public int patch() {
      return version().patch();
    }

    @Override
    public int compareTo(final VersionInfo other) {
      return version.compareTo(other.version);
    }

    public static VersionInfo of(final String s) {
      return VersionInfo.of(SemanticVersion.parse(s).orElse(null));
    }

    public static VersionInfo of(final SemanticVersion version) {
      if (Objects.isNull(version)) {
        return null;
      }

      return new VersionInfo(version, !version.isPreRelease(), false);
    }

    public VersionInfo asLatest() {
      return new VersionInfo(version(), isReleased(), true);
    }

    private VersionInfo asNotLatest() {
      return new VersionInfo(version(), isReleased(), false);
    }
  }

  static class GithubAPI {

    final Retry retry =
        Retry.of(
            "github-api",
            RetryConfig.custom()
                .maxAttempts(10)
                .intervalFunction(IntervalFunction.ofExponentialBackoff())
                .build());

    private HttpRequest.Builder createAuthenticatedRequest(final URI endpoint) {
      final var builder = HttpRequest.newBuilder().GET().uri(endpoint);
      final var token = System.getenv("GH_TOKEN");
      if (token != null && !token.isEmpty()) {
        builder.header("Authorization", "Bearer " + token);
      }
      return builder;
    }

    public Optional<Release> fetchRelease(final SemanticVersion tagName) {
      final var endpoint =
          URI.create(
              "https://api.github.com/repos/camunda/camunda/releases/tags/" + tagName.toString());
      try (final var httpClient = HttpClient.newHttpClient()) {
        final var response =
            retry.executeCallable(
                () ->
                    httpClient.send(
                        createAuthenticatedRequest(endpoint).build(), BodyHandlers.ofByteArray()));

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

    public Stream<Ref> fetchTags() {
      final var endpoint =
          URI.create("https://api.github.com/repos/camunda/camunda/git/matching-refs/tags/8.");
      try (final var httpClient = HttpClient.newHttpClient()) {
        final var response =
            retry.executeCallable(
                () ->
                    httpClient.send(
                        createAuthenticatedRequest(endpoint).build(), BodyHandlers.ofByteArray()));

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
    public record Ref(String ref) {
      SemanticVersion toSemanticVersion() {
        return SemanticVersion.parse(ref.substring("refs/tags/".length())).orElse(null);
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Release(String tag_name) {
      SemanticVersion toSemanticVersion() {
        return SemanticVersion.parse(tag_name).orElse(null);
      }
    }
  }

  private record UpgradePath(SemanticVersion from, SemanticVersion to) {}

  interface VersionProvider {
    Stream<VersionInfo> discoverVersions();
  }

  interface VersionCompatibilityConfig {
    SemanticVersion getCurrentVersion();

    Optional<SemanticVersion> getPreviousMinorVersion();
  }
}
