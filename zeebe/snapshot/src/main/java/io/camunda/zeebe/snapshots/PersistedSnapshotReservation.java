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
    SCALE_UP((short) 1),
    BACKUP((short) 2),
    UNKNOWN((short) 255);

    private final short code;

    Reason(final short code) {
      this.code = code;
    }

    public short code() {
      return code;
    }

    public static Reason fromCode(final short code) {
      return switch (code) {
        case 1 -> SCALE_UP;
        case 2 -> BACKUP;
        default -> UNKNOWN;
      };
    }
  }
}
