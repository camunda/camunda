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
 * Fences job commands against a stale lease token.
 *
 * <p>A stored (persisted) job carries a {@code leaseToken} that is non-empty only when the job was
 * activated with a lease. Incoming commands carry a {@code leaseToken} as well. Two flavors of
 * fencing are provided:
 *
 * <ul>
 *   <li>{@link #forLifecycleCommand()} - used by lifecycle commands (complete, fail, throw error)
 *       that must always supply a matching lease token when the stored job is leased.
 *   <li>{@link #forUpdateCommand()} - used by property-update commands (update, update timeout,
 *       update retries) that only need to match the lease token when both stored and supplied
 *       tokens are present.
 * </ul>
 *
 * Engine-internal transitions (time-out, cancel, yield, recur-after-backoff) should never run these
 * checks.
 *
 * <p>Rejection reasons never include the actual lease token values (stored or supplied) to avoid
 * leaking the token into exported records or logs.
 */
public final class JobLeaseFencingCheck {

  private static final String LEASE_TOKEN_MISSING_MESSAGE =
      "Expected to process job with key '%d', but a matching lease token must be provided "
          + "because the job is currently leased";
  private static final String LEASE_TOKEN_MISMATCH_MESSAGE =
      "Expected to process job with key '%d', but the supplied lease token does not match "
          + "the lease token of the job";

  private JobLeaseFencingCheck() {}

  /**
   * Fences lifecycle commands: if the stored job is leased, the command must supply a matching
   * lease token. If the stored job is not leased, the command is always accepted.
   */
  public static JobCommandCheck forLifecycleCommand() {
    return (command, jobRecord) -> {
      final var storedLeaseToken = jobRecord.getLeaseToken();
      if (storedLeaseToken.isEmpty()) {
        return Either.right(jobRecord);
      }

      final var suppliedLeaseToken = command.getValue().getLeaseToken();
      if (suppliedLeaseToken.isEmpty()) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_STATE,
                LEASE_TOKEN_MISSING_MESSAGE.formatted(command.getKey())));
      }

      if (!suppliedLeaseToken.equals(storedLeaseToken)) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_STATE,
                LEASE_TOKEN_MISMATCH_MESSAGE.formatted(command.getKey())));
      }

      return Either.right(jobRecord);
    };
  }

  /**
   * Fences property-update commands: only rejects when both the stored job and the command supply a
   * non-empty lease token and they differ so that job workers can use the lease mechanism for
   * updates. Any other combination (stored empty, or supplied empty) is accepted such that leased
   * jobs can still be updated without a lease by operators.
   */
  public static JobCommandCheck forUpdateCommand() {
    return (command, jobRecord) -> {
      final var storedLeaseToken = jobRecord.getLeaseToken();
      final var suppliedLeaseToken = command.getValue().getLeaseToken();

      if (storedLeaseToken.isEmpty() || suppliedLeaseToken.isEmpty()) {
        return Either.right(jobRecord);
      }

      if (!suppliedLeaseToken.equals(storedLeaseToken)) {
        return Either.left(
            new Rejection(
                RejectionType.INVALID_STATE,
                LEASE_TOKEN_MISMATCH_MESSAGE.formatted(command.getKey())));
      }

      return Either.right(jobRecord);
    };
  }
}
