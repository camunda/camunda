/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for truncating tree path strings by logical segments while preserving as much
 * structure as possible.
 *
 * <p>A tree path consists of segments separated by '/', where each segment is either:
 *
 * <ul>
 *   <li>"PI_x/FN_y/FNI_z" (if a process instance has an outgoing call edge)
 *   <li>"PI_x" (for a tail, i.e., the last process instance)
 * </ul>
 *
 * <p>This class provides methods to truncate such paths to fit within a specified maximum length,
 * prioritizing the retention of full segments from the start and end of the path.
 */
public final class TreePathTruncator {

  private static final Logger LOG = LoggerFactory.getLogger(TreePathTruncator.class);

  /**
   * Truncates a tree path string by logical segments, ensuring the result does not exceed the
   * specified maximum length.
   *
   * <p>A tree path consists of segments separated by '/', where each segment is either:
   *
   * <ul>
   *   <li>"PI_x/FN_y/FNI_z" (if a process instance has an outgoing call edge)
   *   <li>"PI_x" (for a tail, i.e., the last process instance)
   * </ul>
   *
   * <p>The method attempts to keep as many full segments as possible from the start (prefix) and
   * end (suffix) of the path, removing segments from the middle if necessary. If no valid
   * truncation fits, it falls back to the minimal form (first and last segment only), and if that
   * still does not fit, it performs a hard character-based truncation.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>PI_0
   *   <li>PI_0/FN_0/FNI_0/PI_1
   *   <li>PI_0/FN_0/FNI_0/PI_1/FN_1/FNI_1/PI_2
   * </ul>
   *
   * @param treePath the full tree path string to truncate (must not be null)
   * @param maxSize the maximum allowed length of the result string (must be positive)
   * @return the truncated tree path, preserving as much logical structure as possible
   */
  public static String truncateTreePath(final String treePath, final int maxSize) {
    Objects.requireNonNull(treePath, "treePath");
    if (maxSize <= 0) {
      return "";
    }
    if (treePath.length() <= maxSize) {
      return treePath;
    }

    final var segments = parseSegments(treePath);
    // If the path is empty or only a single segment, fall back to hard truncation
    if (segments.isEmpty()) {
      LOG.warn(
          "Unable to parse tree path segments from: {}, falling back to a hard truncate", treePath);
      return hardTruncate(treePath, maxSize);
    } else if (segments.size() == 1) {
      LOG.warn("Tree path has a single segment: {}, falling back to a hard truncate", treePath);
      return hardTruncate(treePath, maxSize);
    }

    final var segmentCount = segments.size();
    // Try to find the best truncation candidate (max fill, most segments, balanced)
    final Optional<TruncationCandidate> bestCandidate =
        findBestTruncationCandidate(segments, segmentCount, maxSize);
    if (bestCandidate.isPresent()) {
      return bestCandidate.get().value();
    }

    // If nothing fits, try the minimal form (first + last segment)
    final var minimal = buildCandidateSegment(segments, 1, 1);
    if (minimal.length() <= maxSize) {
      return minimal;
    }
    // As a last resort, truncate by character count
    LOG.warn(
        "Tree path cannot be truncated by segments to fit max size: {}, falling back to hard truncate",
        treePath);

    return hardTruncate(minimal, maxSize);
  }

  /**
   * Finds the best truncation candidate by evaluating all valid prefix/suffix combinations.
   *
   * <p>The best candidate is the one that:
   *
   * <ul>
   *   <li>Has the maximum possible length not exceeding {@code maxSize}
   *   <li>Keeps the most segments (prefix + suffix)
   *   <li>Has the most balanced prefix and suffix (smallest absolute difference)
   * </ul>
   *
   * @param segments the parsed logical segments of the tree path
   * @param segmentCount the total number of segments
   * @param maxSize the maximum allowed length of the result string
   * @return an {@link Optional} containing the best candidate, or empty if none fit
   */
  private static Optional<TruncationCandidate> findBestTruncationCandidate(
      final List<String> segments, final int segmentCount, final int maxSize) {
    // We binary search on the total number of kept segments (prefix + suffix).
    // For a fixed number of kept segments, adding more segments always increases
    // the resulting candidate length, so the globally longest valid candidate
    // will always use the maximum number of kept segments that still fits.
    int left = 2; // at least one prefix and one suffix
    int right = segmentCount - 1; // must remove at least one middle segment
    Optional<TruncationCandidate> bestCandidate = Optional.empty();

    while (left <= right) {
      final int mid = left + (right - left) / 2;
      final Optional<TruncationCandidate> candidateForMid =
          findBestCandidateForKeptSegments(segments, segmentCount, mid, maxSize);

      if (candidateForMid.isPresent()) {
        // mid kept segments fit; try to see if we can keep even more
        bestCandidate = candidateForMid;
        left = mid + 1;
      } else {
        // mid kept segments do not fit; try fewer
        right = mid - 1;
      }
    }

    return bestCandidate;
  }

  /**
   * Finds the best candidate for a fixed number of kept segments (prefix + suffix).
   *
   * <p>For a given {@code keptSegments}, this will:
   *
   * <ul>
   *   <li>Iterate over all valid prefix/suffix splits where {@code prefixCount + suffixCount =
   *       keptSegments} and at least one middle segment is removed
   *   <li>Create candidates that fit within {@code maxSize}
   *   <li>Select the candidate with the greatest length, then the most balanced prefix/suffix
   *       (smallest absolute difference)
   * </ul>
   *
   * @param segments the parsed logical segments of the tree path
   * @param segmentCount the total number of segments
   * @param keptSegments the total number of segments to keep (prefix + suffix)
   * @param maxSize the maximum allowed length of the result string
   * @return an {@link Optional} containing the best candidate for this kept-segment count, or
   *     empty if none fit
   */
  private static Optional<TruncationCandidate> findBestCandidateForKeptSegments(
      final List<String> segments,
      final int segmentCount,
      final int keptSegments,
      final int maxSize) {
    // Must remove at least one segment from the middle
    if (segmentCount - keptSegments <= 0) {
      return Optional.empty();
    }

    return IntStream.rangeClosed(1, keptSegments - 1)
        .mapToObj(
            prefixCount -> {
              final int suffixCount = keptSegments - prefixCount;
              return createCandidate(
                  segments, segmentCount, prefixCount, suffixCount, maxSize);
            })
        .filter(Optional::isPresent)
        .map(Optional::get)
        // For a fixed keptSegments, prefer longest and then most balanced
        .max(
            Comparator.comparingInt(TruncationCandidate::length)
                .thenComparingInt(c -> -c.prefixSuffixBalance()));
  }

  /**
   * Attempts to create a truncation candidate for the given prefix and suffix segment counts.
   *
   * @param segments the parsed logical segments of the tree path
   * @param segmentCount the total number of segments
   * @param prefixCount the number of segments to keep from the start (prefix)
   * @param suffixCount the number of segments to keep from the end (suffix)
   * @param maxSize the maximum allowed length of the result string
   * @return an {@link Optional} containing the candidate if it fits, or empty if not valid
   */
  private static Optional<TruncationCandidate> createCandidate(
      final List<String> segments,
      final int segmentCount,
      final int prefixCount,
      final int suffixCount,
      final int maxSize) {
    final int removedSegments = segmentCount - prefixCount - suffixCount;
    // Must remove at least one segment from the middle
    if (removedSegments <= 0) {
      return Optional.empty();
    }
    final var candidate = buildCandidateSegment(segments, prefixCount, suffixCount);
    final var candidateLength = candidate.length();
    if (candidateLength > maxSize) {
      return Optional.empty();
    }
    final var keptSegments = prefixCount + suffixCount;
    final var prefixSuffixBalance = Math.abs(prefixCount - suffixCount);
    return Optional.of(
        new TruncationCandidate(candidate, candidateLength, keptSegments, prefixSuffixBalance));
  }

  /**
   * Parses a tree path string into logical segments.
   *
   * <p>Each segment starts with a "PI_" token, and may be followed by "FN_" and "FNI_" tokens.
   * Segments are separated by '/'.
   *
   * <p>For example:
   *
   * <ul>
   *   <li>PI_0
   *   <li>PI_0/FN_0/FNI_0/PI_1
   *   <li>PI_0/FN_0/FNI_0/PI_1/FN_1/FNI_1/PI_2
   * </ul>
   *
   * @param treePath the tree path string to parse
   * @return a list of logical segments, or an empty list if the format is invalid
   */
  private static List<String> parseSegments(final String treePath) {
    final var tokens = Stream.of(treePath.split("/")).filter(t -> !t.isEmpty()).toList();
    if (tokens.isEmpty()) {
      return List.of();
    }
    final var segments = new ArrayList<String>();
    var i = 0;
    while (i < tokens.size()) {
      final var processInstanceToken = tokens.get(i);
      // Each segment must start with PI_
      if (!processInstanceToken.startsWith("PI_")) {
        return List.of();
      }
      i++;
      final var treePathSegment = new StringBuilder(processInstanceToken);
      // Attach FN_ and FNI_ tokens if present (as part of the same segment)
      if (i + 1 < tokens.size()
          && tokens.get(i).startsWith("FN_")
          && tokens.get(i + 1).startsWith("FNI_")) {
        treePathSegment.append('/').append(tokens.get(i));
        treePathSegment.append('/').append(tokens.get(i + 1));
        i += 2;
      }
      segments.add(treePathSegment.toString());
      // Next token must be PI_ or end of tokens; otherwise, format is invalid
      if (i < tokens.size() && !tokens.get(i).startsWith("PI_")) {
        return List.of();
      }
    }
    return segments;
  }

  /**
   * Builds a candidate tree path string by joining the specified number of prefix and suffix
   * segments.
   *
   * @param segments the list of logical segments
   * @param prefixCount the number of segments to keep from the start
   * @param suffixCount the number of segments to keep from the end
   * @return the joined candidate string
   */
  private static String buildCandidateSegment(
      final List<String> segments, final int prefixCount, final int suffixCount) {
    final var prefixStr =
        IntStream.range(0, prefixCount).mapToObj(segments::get).collect(Collectors.joining("/"));
    final var suffixStr =
        IntStream.range(segments.size() - suffixCount, segments.size())
            .mapToObj(segments::get)
            .collect(Collectors.joining("/"));
    // Only join non-empty prefix/suffix parts
    return Stream.of(prefixStr, suffixStr)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.joining("/"));
  }

  /**
   * Performs a hard character-based truncation of the input string if it exceeds the specified
   * maximum size.
   *
   * @param s the string to truncate
   * @param maxSize the maximum allowed length
   * @return the truncated string, or the original if it fits
   */
  private static String hardTruncate(final String s, final int maxSize) {
    return s.length() <= maxSize ? s : s.substring(0, maxSize);
  }

  /**
   * Represents a possible truncation candidate for the tree path, including its value, length,
   * number of kept segments, and prefix/suffix balance. Used for comparing and selecting the best
   * truncation result.
   */
  private record TruncationCandidate(
      String value, int length, int keptSegments, int prefixSuffixBalance) {}
}
