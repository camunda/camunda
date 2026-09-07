/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.support;

import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Shared index-prefix rules for Elasticsearch, OpenSearch and the CamundaExporter. */
@NullMarked
public final class IndexPrefixValidation {

  // half of ES/OS's 255-char index-name limit, leaving headroom for the generated suffix to grow
  public static final int MAX_PREFIX_LENGTH = 127;
  // ES/OS-illegal chars, plus `_`, which is used by the legacy exporters as the prefix delimiter
  private static final Pattern INVALID_CHARACTERS = Pattern.compile("[\\\\/*?\"<>| _,#:]");

  private IndexPrefixValidation() {}

  public static boolean hasInvalidCharacters(final @Nullable String prefix) {
    return prefix != null && INVALID_CHARACTERS.matcher(prefix).find();
  }

  public static boolean hasInvalidLeadingCharacter(final @Nullable String prefix) {
    return prefix != null
        && (prefix.startsWith(".")
            || prefix.startsWith("+")
            || prefix.startsWith("-")
            || prefix.startsWith("_"));
  }

  public static boolean exceedsMaxLength(final @Nullable String prefix) {
    return prefix != null && prefix.length() > MAX_PREFIX_LENGTH;
  }
}
