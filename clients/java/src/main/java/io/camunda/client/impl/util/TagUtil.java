/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client.impl.util;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class for validating tags used in Zeebe. Tags are used to categorize and label resources
 * within the system.
 *
 * <p>NOTE: Whenever you adjust the tag format or max number, also consider updating the REST API
 * documentation and the duplicated zeebe engine validation (see TagUtil.java)
 */
public class TagUtil {

  public static final int MAX_NUMBER_OF_TAGS = 10;
  public static final int MAX_TAG_LENGTH = 100;
  public static final String TAG_FORMAT_DESCRIPTION =
      String.format(
          "Tags must start with a letter (a-z, A-Z), followed by alphanumerics, underscores, minuses, colons, or periods. "
              + "It must not be blank and must be %s characters or less.",
          MAX_TAG_LENGTH);

  // Pattern for valid tag format: starts with letter, followed by alphanumerics, _, -, :, .
  private static final Pattern VALID_TAG_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_\\-:.]*$");

  /**
   * Validates that a tag follows the required format and constraints: - Must start with a letter
   * (a-z, A-Z) - After the first character, may contain: alphanumerics, underscores, minuses,
   * colons, periods - Must not be blank and must be 100 characters or less
   */
  public static boolean isValidTag(final String tag) {
    return tag != null
        && tag.length() <= MAX_TAG_LENGTH
        && VALID_TAG_PATTERN.matcher(tag).matches();
  }

  public static void ensureValidTags(final String context, final Set<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return;
    }

    if (tags.size() > MAX_NUMBER_OF_TAGS) {
      throw new IllegalArgumentException(
          context
              + ": Tags must not contain more than "
              + MAX_NUMBER_OF_TAGS
              + " tags, but found "
              + tags.size());
    }

    final List<String> invalidTags =
        tags.stream().filter(tag -> !isValidTag(tag)).collect(Collectors.toList());

    if (!invalidTags.isEmpty()) {
      throw new IllegalArgumentException(
          context
              + ": "
              + TAG_FORMAT_DESCRIPTION
              + ". Invalid tags: "
              + invalidTags.stream().collect(Collectors.joining("', '"))
              + "'");
    }
  }
}
