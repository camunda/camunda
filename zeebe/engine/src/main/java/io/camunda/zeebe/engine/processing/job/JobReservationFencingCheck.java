/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;

/**
 * Fences every command on a reserved job against the reservation token.
 *
 * <p>A job of a process instance created with a {@code jobReservationToken} is handed to no job
 * worker: the caller that reserved the instance drives it, so every command on it must carry the
 * same token. A job whose instance was not reserved is always accepted here and left to the lease
 * checks in {@link JobLeaseFencingCheck}.
 *
 * <p>Unlike a lease, this applies to property-update commands too. A lease deliberately lets an
 * operator update a leased job without a token, but a reservation is exclusivity rather than
 * staleness fencing: as long as the instance is reserved, only its creator may touch its jobs.
 *
 * <p>Engine-internal transitions (time-out, cancel, yield, recur-after-backoff) should never run
 * this check.
 *
 * <p>Rejection reasons never include the actual token values (stored or supplied) to avoid leaking
 * them into exported records or logs.
 */
public final class JobReservationFencingCheck {

  private static final String TOKEN_MISSING_MESSAGE =
      "Expected to process job with key '%d', but a matching reservation token must be provided "
          + "because the job's process instance was created with one";
  private static final String TOKEN_MISMATCH_MESSAGE =
      "Expected to process job with key '%d', but the supplied reservation token does not match "
          + "the one its process instance was created with.";

  private JobReservationFencingCheck() {}

  public static JobCommandCheck forCommand() {
    return (command, jobRecord) -> {
      final var storedToken = jobRecord.getJobReservationToken();
      if (storedToken.isEmpty()) {
        return Either.right(jobRecord);
      }

      final var suppliedToken = command.getValue().getJobReservationToken();
      if (suppliedToken.isEmpty()) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_STATE, TOKEN_MISSING_MESSAGE.formatted(command.getKey())));
      }

      if (!suppliedToken.equals(storedToken)) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_STATE, TOKEN_MISMATCH_MESSAGE.formatted(command.getKey())));
      }

      return Either.right(jobRecord);
    };
  }
}
