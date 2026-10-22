/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * ULIDs, the ids the Event Plane makes up for segments and retention markers: 48 bits of
 * milliseconds and 80 random bits, in 26 characters of Crockford's base 32. Sorted as text, they
 * sort by time, so a new object's key lands at the end of its listing.
 */
public final class Ulid {

  private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final Pattern PATTERN = Pattern.compile("[0-7][0-9A-HJKMNP-TV-Z]{25}");
  private static final SecureRandom RANDOM = new SecureRandom();

  private Ulid() {}

  /** A new ULID, of now. */
  public static String next() {
    final var randomness = new byte[10];
    RANDOM.nextBytes(randomness);
    return of(System.currentTimeMillis(), randomness);
  }

  /** Whether the text is a ULID: 26 characters of the alphabet, and at most 48 bits of time. */
  public static boolean isValid(final String text) {
    return PATTERN.matcher(text).matches();
  }

  /** The ULID of a time, in milliseconds since the epoch, and 10 random bytes. */
  static String of(final long time, final byte[] randomness) {
    if (time < 0 || time >= 1L << 48 || randomness.length != 10) {
      throw new IllegalArgumentException(
          "Expected a time of 0 to 2^48 - 1 milliseconds and 10 random bytes, but got %d milliseconds and %d bytes"
              .formatted(time, randomness.length));
    }
    long millis = time;
    final var ulid = new char[26];
    for (int i = 9; i >= 0; i--, millis >>>= 5) {
      ulid[i] = ALPHABET[(int) (millis & 31)];
    }
    // The 80 random bits as their upper 16 and lower 64, shifted out 5 at a time.
    long high = (randomness[0] & 0xFFL) << 8 | randomness[1] & 0xFFL;
    long low = 0;
    for (int i = 2; i < 10; i++) {
      low = low << 8 | randomness[i] & 0xFFL;
    }
    for (int i = 25; i >= 10; i--) {
      ulid[i] = ALPHABET[(int) (low & 31)];
      low = low >>> 5 | (high & 31) << 59;
      high >>>= 5;
    }
    return new String(ulid);
  }
}
