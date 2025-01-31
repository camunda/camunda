/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public class CommonFixtures {

  public static final OffsetDateTime NOW = OffsetDateTime.now();
  protected static final Random RANDOM = new Random(System.nanoTime());
  private static final AtomicLong ID_COUNTER = new AtomicLong(54321L);

  public static Long nextKey() {
    return ID_COUNTER.incrementAndGet();
  }

  public static String nextStringId() {
    return UUID.randomUUID().toString();
  }

  public static String generateRandomString(final int length) {
    final StringBuilder sb = new StringBuilder();
    while (sb.length() < length) {
      sb.append(UUID.randomUUID().toString().replace("-", ""));
    }
    return sb.substring(0, length);
  }

  public static String generateRandomString(final int length, final String prefix) {
    return "%s-%s".formatted(generateRandomString(length), prefix);
  }

  public static String generateRandomString(final String prefix) {
    return "%s-%s".formatted(generateRandomString(10), prefix);
  }

  public static List<String> generateRandomStrings(final String prefix, final int n) {
    return IntStream.range(0, n)
        .mapToObj(i -> generateRandomString("%s%02d".formatted(prefix, i)))
        .toList();
  }

  /** Random String which contains random text values, as well es Doubles or Longs */
  public static String generateRandomStringWithRandomTypes() {
    return switch (RANDOM.nextInt(10)) {
      case 0, 1, 2, 3, 4, 5 ->
          // Normal String
          "variable-value-" + RANDOM.nextInt(1000);
      case 6 ->
          // Long
          String.valueOf(RANDOM.nextLong(1000));
      case 7 ->
          // Long
          String.valueOf(RANDOM.nextLong(1000) * -1);
      case 8 -> (String.valueOf(RANDOM.nextDouble(1000)));
      case 9 -> (String.valueOf(RANDOM.nextDouble(1000) * -1));
      default -> throw new IllegalStateException("Unexpected value");
    };
  }

  public static <T extends Enum<?>> T randomEnum(final Class<T> clazz) {
    final int x = RANDOM.nextInt(clazz.getEnumConstants().length);
    return clazz.getEnumConstants()[x];
  }
}
