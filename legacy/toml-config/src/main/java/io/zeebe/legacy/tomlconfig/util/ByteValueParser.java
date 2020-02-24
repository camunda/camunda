/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.legacy.tomlconfig.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Deprecated(since = "0.23.0-alpha2", forRemoval = true)
/* Kept in order to be able to offer a migration path for old configurations.
 */
public class ByteValueParser {
  private static final Pattern PATTERN =
      Pattern.compile("(\\d+)([K|M|G]?)", Pattern.CASE_INSENSITIVE);

  @Deprecated
  public static ByteValue fromString(final String humanReadable) {

    final Matcher matcher = PATTERN.matcher(humanReadable);

    if (!matcher.matches()) {
      final String err =
          String.format(
              "Illegal byte value '%s'. Must match '%s'. Valid examples: 100M, 4K, ...",
              humanReadable, PATTERN.pattern());
      throw new IllegalArgumentException(err);
    }

    final String valueString = matcher.group(1);
    final long value = Long.parseLong(valueString);

    final String unitString = matcher.group(2).toUpperCase();

    final ByteUnit unit = ByteUnit.getUnit(unitString);

    return new ByteValue(value, unit);
  }

  @Deprecated
  public static ByteValue ofBytes(final long value) {
    return new ByteValue(value, ByteUnit.BYTES);
  }
}
