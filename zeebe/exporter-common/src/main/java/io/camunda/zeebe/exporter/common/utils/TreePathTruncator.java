/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for truncating tree path strings by logical segments while preserving as much
 * structure as possible.
 *
 * <p>This class supports two tree path formats:
 *
 * <ol>
 *   <li><b>Prefixed format (inter-tree paths):</b> Segments with PI_/FN_/FNI_ prefixes:
 *       <ul>
 *         <li>"PI_x/FN_y/FNI_z" (if a process instance has an outgoing call edge)
 *         <li>"PI_x" (for a tail, i.e., the last process instance)
 *       </ul>
 *   <li><b>Unprefixed format (intra-tree paths):</b> Plain numeric tokens separated by '/', where
 *       each token represents a segment (e.g., "123/456/789")
 * </ol>
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
   * <p>This method supports two tree path formats:
   *
   * <ol>
   *   <li><b>Prefixed format (inter-tree paths):</b> Segments with PI_/FN_/FNI_ prefixes:
   *       <ul>
   *         <li>"PI_x/FN_y/FNI_z" (if a process instance has an outgoing call edge)
   *         <li>"PI_x" (for a tail, i.e., the last process instance)
   *       </ul>
   *   <li><b>Unprefixed format (intra-tree paths):</b> Plain numeric tokens separated by '/' (e.g.,
   *       "123/456/789")
   * </ol>
   *
   * <p>The method attempts to keep as many full segments as possible from the start (prefix) and
   * end (suffix) of the path, removing a continuous sequence of segments from the middle if
   * necessary. The truncation algorithm:
   *
   * <ol>
   *   <li>Always keeps the first segment (root) and last segment (leaf)
   *   <li>Iteratively adds segments from left and right, preferring the shorter segment at each
   *       step
   *   <li>Balances additions between left and right to avoid bias toward one side
   *   <li>Continues until adding the next segment would exceed {@code maxSize}
   * </ol>
   *
   * <p>If no valid truncation fits (e.g., even first + last segment exceeds maxSize), it falls back
   * to hard character-based truncation.
   *
   * <p><b>Example of prefixed path truncation:</b>
   *
   * <pre>
   * Input:  PI_0/FN_0/FNI_0/PI_1/FN_1/FNI_1/PI_2/FN_2/FNI_2/PI_3
   * Output: PI_0/FN_0/FNI_0/PI_1/PI_3
   *         (kept first 2 segments + last segment, removed middle segments PI_2/FN_2/FNI_2)
   * </pre>
   *
   * <p><b>Example of unprefixed path truncation:</b>
   *
   * <pre>
   * Input:  123/456/789/101112/131415/161718
   * Output: 123/456/161718
   *         (kept first 2 segments + last segment, removed middle segments)
   * </pre>
   *
   * @param treePath the full tree path string to truncate (must not be null)
   * @param maxSize the maximum allowed length of the result string (must be positive)
   * @return the truncated tree path, preserving as much logical structure as possible
   */
  public static String truncateTreePath(final String treePath, final int maxSize) {
    Objects.requireNonNull(treePath, "treePath");
    if (maxSize <= 0) {
      LOG.warn("maxSize is {} which is non-positive, returning empty string", maxSize);
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

    // Try to find the best truncation using simplified incremental algorithm
    final String result = findBestTruncationIncremental(segments, maxSize);
    if (result != null) {
      return result;
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
   * Finds the best truncation using a simplified incremental algorithm.
   *
   * <p>The algorithm:
   *
   * <ol>
   *   <li>Always starts with the first segment (root) and last segment (leaf)
   *   <li>Iteratively adds the next available segment from either left or right
   *   <li>Chooses the shorter segment between left candidate and right candidate
   *   <li>Applies a balancing heuristic to avoid always picking from one side
   *   <li>Continues until adding the next segment would exceed {@code maxSize}
   * </ol>
   *
   * <p>This approach is simpler and more predictable than exhaustively testing all combinations.
   *
   * @param segments the parsed logical segments of the tree path
   * @param maxSize the maximum allowed length of the result string
   * @return the truncated path string, or null if even the minimal form doesn't fit
   */
  private static String findBestTruncationIncremental(
      final List<String> segments, final int maxSize) {
    final int segmentCount = segments.size();
    int prefixCount = 1; // Always keep first segment (root)
    int suffixCount = 1; // Always keep last segment (leaf)

    // Check if minimal form (first + last) fits
    String current = buildCandidateSegment(segments, prefixCount, suffixCount);
    if (current.length() > maxSize) {
      return null; // Even minimal form doesn't fit
    }

    // Incrementally add segments from left or right
    while (prefixCount + suffixCount < segmentCount) {
      // Determine next candidates
      final int nextPrefixIdx = prefixCount; // Index of next left segment to add
      final int nextSuffixIdx =
          segmentCount - suffixCount - 1; // Index of next right segment to add

      // If they meet in the middle, we've used all segments
      if (nextPrefixIdx > nextSuffixIdx) {
        break;
      }

      final String leftCandidate = segments.get(nextPrefixIdx);
      final String rightCandidate = segments.get(nextSuffixIdx);

      // Decide whether to add from left or right
      final boolean addFromLeft =
          shouldAddFromLeft(leftCandidate, rightCandidate, prefixCount, suffixCount);

      // Try adding the chosen segment
      final int newPrefixCount = addFromLeft ? prefixCount + 1 : prefixCount;
      final int newSuffixCount = addFromLeft ? suffixCount : suffixCount + 1;
      final String candidate = buildCandidateSegment(segments, newPrefixCount, newSuffixCount);

      if (candidate.length() > maxSize) {
        // Adding this segment would exceed maxSize, stop here
        break;
      }

      // Accept the new segment
      prefixCount = newPrefixCount;
      suffixCount = newSuffixCount;
      current = candidate;
    }

    return current;
  }

  /**
   * Decides whether to add a segment from the left or right side.
   *
   * <p>The decision is based on:
   *
   * <ul>
   *   <li>Preferring the shorter segment (to maximize total segments kept)
   *   <li>Applying a balance heuristic to avoid always picking from the same side when segments are
   *       equal length
   * </ul>
   *
   * @param leftCandidate the next segment from the left
   * @param rightCandidate the next segment from the right
   * @param currentPrefixCount the current number of prefix segments
   * @param currentSuffixCount the current number of suffix segments
   * @return true to add from left, false to add from right
   */
  private static boolean shouldAddFromLeft(
      final String leftCandidate,
      final String rightCandidate,
      final int currentPrefixCount,
      final int currentSuffixCount) {
    final int leftLength = leftCandidate.length();
    final int rightLength = rightCandidate.length();

    if (leftLength < rightLength) {
      return true; // Prefer shorter segment from left
    } else if (rightLength < leftLength) {
      return false; // Prefer shorter segment from right
    } else {
      // Same length: apply balance heuristic
      // Prefer adding to the side with fewer segments to maintain balance
      return currentPrefixCount <= currentSuffixCount;
    }
  }

  /**
   * Parses a tree path string into logical segments.
   *
   * <p>This method supports two formats:
   *
   * <ol>
   *   <li><b>Prefixed format (inter-tree paths):</b> Each segment starts with a "PI_" token, and
   *       may be followed by "FN_" and "FNI_" tokens. Examples:
   *       <ul>
   *         <li>PI_0
   *         <li>PI_0/FN_0/FNI_0/PI_1
   *         <li>PI_0/FN_0/FNI_0/PI_1/FN_1/FNI_1/PI_2
   *       </ul>
   *   <li><b>Unprefixed format (intra-tree paths):</b> Plain numeric tokens separated by '/'.
   *       Examples:
   *       <ul>
   *         <li>123
   *         <li>123/456
   *         <li>123/456/789
   *       </ul>
   * </ol>
   *
   * @param treePath the tree path string to parse
   * @return a list of logical segments, or an empty list if the format is invalid
   */
  private static List<String> parseSegments(final String treePath) {
    final var tokens = Stream.of(treePath.split("/")).filter(t -> !t.isEmpty()).toList();
    if (tokens.isEmpty()) {
      return List.of();
    }

    // Check if this is a prefixed path (first token starts with PI_)
    if (tokens.get(0).startsWith("PI_")) {
      return parsePrefixedSegments(tokens);
    } else {
      // Unprefixed path: each token is its own segment
      return new ArrayList<>(tokens);
    }
  }

  /**
   * Parses prefixed (inter-tree) path segments.
   *
   * <p>Each segment starts with a "PI_" token, and may be followed by "FN_" and "FNI_" tokens.
   *
   * @param tokens the tokens to parse
   * @return a list of logical segments, or an empty list if the format is invalid
   */
  private static List<String> parsePrefixedSegments(final List<String> tokens) {
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
}
