/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.clustermetadata.processing;

import io.camunda.eventbridge.clustermetadata.record.BrokerRecord;
import io.camunda.eventbridge.clustermetadata.state.broker.BrokerMetadata.BrokerStatus;
import io.camunda.eventbridge.clustermetadata.state.immutable.BrokerState;
import io.camunda.zeebe.util.Either;

/**
 * Validation for the broker registry's <em>internal</em> state-machine commands — {@code
 * FENCE_BROKER}, {@code DRAIN_BROKER}, {@code DEREGISTER_BROKER} — the broker counterpart of the
 * consumer-groups {@code TransitionValidator}. These are appended by the eviction task / heartbeat
 * handler, not clients, so a failed check is not a client error code but a reason the command is
 * dropped: each check returns {@code Either<String, Void>} (the reason on the left), which the
 * processor turns into a {@code COMMAND_REJECTION} with no reply. The guards encode the legal
 * broker liveness transitions against the current replicated {@link BrokerState}; this runs on the
 * stream-processing actor.
 */
public final class BrokerTransitionValidator {

  private static final Either<String, Void> VALID = Either.right(null);

  private final BrokerState state;

  public BrokerTransitionValidator(final BrokerState state) {
    this.state = state;
  }

  /** A fence is valid while the broker still exists, is {@code ACTIVE}, and the epoch matches. */
  public Either<String, Void> validateFence(final BrokerRecord command) {
    return requireActiveAtEpoch(command);
  }

  /** A drain is valid while the broker still exists, is {@code ACTIVE}, and the epoch matches. */
  public Either<String, Void> validateDrain(final BrokerRecord command) {
    return requireActiveAtEpoch(command);
  }

  /** A deregister is valid only while the broker still exists. */
  public Either<String, Void> validateDeregister(final BrokerRecord command) {
    if (state.get(command.getBrokerId()) == null) {
      return Either.left("broker %d is no longer registered".formatted(command.getBrokerId()));
    }
    return VALID;
  }

  private Either<String, Void> requireActiveAtEpoch(final BrokerRecord command) {
    final var broker = state.get(command.getBrokerId());
    if (broker == null) {
      return Either.left("broker %d is no longer registered".formatted(command.getBrokerId()));
    }
    if (broker.status() != BrokerStatus.ACTIVE) {
      return Either.left(
          "broker %d is %s, not ACTIVE".formatted(command.getBrokerId(), broker.status()));
    }
    if (broker.brokerEpoch() != command.getBrokerEpoch()) {
      return Either.left(
          "stale command: epoch %d != broker epoch %d"
              .formatted(command.getBrokerEpoch(), broker.brokerEpoch()));
    }
    return VALID;
  }
}
