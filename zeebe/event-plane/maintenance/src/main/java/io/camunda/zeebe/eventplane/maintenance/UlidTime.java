/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.store.InvalidObjectNameException;
import io.camunda.zeebe.eventplane.store.Ulid;
import java.time.Instant;

/** Reads the time that a {@link Ulid} holds. */
final class UlidTime {

  private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  private static final int TIME_CHARACTERS = 10;
  private static final int BITS_PER_CHARACTER = 5;

  private UlidTime() {}

  /**
   * The time in the ULID's first 10 characters: its 48 bits of milliseconds since the epoch, 5 bits
   * to a character.
   *
   * @throws InvalidObjectNameException if the text isn't a ULID
   */
  static Instant of(final String ulid) {
    if (!Ulid.isValid(ulid)) {
      throw new InvalidObjectNameException("a ULID", ulid);
    }
    long millis = 0;
    for (int i = 0; i < TIME_CHARACTERS; i++) {
      millis = millis << BITS_PER_CHARACTER | ALPHABET.indexOf(ulid.charAt(i));
    }
    return Instant.ofEpochMilli(millis);
  }
}
