/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Only the broker itself writes policy update commands, on the deployment partition, which
 * coordinates every policy update. The policy update processors therefore check no user
 * authorization, but reject a command that came from a client request or that was written to
 * another partition.
 */
@NullMarked
final class PolicyUpdateGuard {

  private PolicyUpdateGuard() {}

  /** Rejects a command that came from a client request or that was written to another partition. */
  static Optional<Rejection> checkInternal(final TypedRecord<PolicyUpdateRecord> command) {
    return checkNotFromClient(command).or(() -> checkOnDeploymentPartition(command));
  }

  /**
   * Rejects a command that came from a client request. Every command from the command API carries
   * request metadata; a command the broker writes itself, or distributes, carries none.
   */
  static Optional<Rejection> checkNotFromClient(final TypedRecord<PolicyUpdateRecord> command) {
    if (!command.hasRequestMetadata()) {
      return Optional.empty();
    }
    return Optional.of(
        new Rejection(
            RejectionType.FORBIDDEN,
            "Expected policy update '%s' to be written by the broker, but it was sent as a client request"
                .formatted(command.getValue().getUpdateId())));
  }

  private static Optional<Rejection> checkOnDeploymentPartition(
      final TypedRecord<PolicyUpdateRecord> command) {
    if (command.getPartitionId() == Protocol.DEPLOYMENT_PARTITION) {
      return Optional.empty();
    }
    return Optional.of(
        new Rejection(
            RejectionType.INVALID_STATE,
            "Expected policy update '%s' to be applied on partition %d, but it was written to partition %d"
                .formatted(
                    command.getValue().getUpdateId(),
                    Protocol.DEPLOYMENT_PARTITION,
                    command.getPartitionId())));
  }
}
