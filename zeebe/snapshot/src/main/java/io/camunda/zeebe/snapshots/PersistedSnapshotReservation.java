/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots;

import java.util.UUID;

public interface PersistedSnapshotReservation extends SnapshotReservation {
  UUID reservationId();

  long validUntil();

  Reason reason();

  enum Reason {
    SCALE_UP((byte) 1),
    BACKUP((byte) 2);

    private static final Reason[] VALUES = values();
    private final byte code;

    Reason(final byte code) {
      this.code = code;
    }

    public byte code() {
      return code;
    }

    public static Reason fromCode(final byte code) {
      try {
        return VALUES[code - 1];
      } catch (final ArrayIndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Unknown reason code: " + code);
      }
    }
  }
}
