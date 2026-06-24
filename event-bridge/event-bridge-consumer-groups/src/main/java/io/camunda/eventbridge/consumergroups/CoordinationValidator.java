/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups;

import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.FENCED_MEMBER_EPOCH;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.INVALID_GROUP_ID;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.NOT_PARTITION_OWNER;
import static io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode.UNKNOWN_MEMBER_ID;

import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;
import io.camunda.zeebe.util.Either;

public record CoordinationValidator(ConsumerGroupRegistry registry) {

  public Either<CoordinationErrorCode, Boolean> isActiveMember(
      final String groupId, final String memberId) {
    if (!registry.isConsumerActive(groupId, memberId)) {
      return Either.left(UNKNOWN_MEMBER_ID);
    }
    return Either.right(true);
  }

  public Either<CoordinationErrorCode, Boolean> isGroupIdValid(final String groupId) {
    if (groupId == null || groupId.isEmpty()) {
      return Either.left(INVALID_GROUP_ID);
    }
    return Either.right(true);
  }

  public Either<CoordinationErrorCode, Boolean> isValidMemberEpoch(
      final long memberEpoch, final long expectedMemberEpoch) {
    if (expectedMemberEpoch > memberEpoch) {
      return Either.left(FENCED_MEMBER_EPOCH);
    } else if (expectedMemberEpoch != memberEpoch) {
      return Either.left(UNKNOWN_MEMBER_ID);
    }

    return Either.right(true);
  }

  /**
   * Fences offset commits to the partitions the coordinator currently designates the member as
   * owning (its confirmed or current target assignment), so a lagging or zombie consumer cannot
   * commit into a partition another consumer now owns. Monotonic commits already prevent rewinds;
   * this rejects the write outright.
   */
  public Either<CoordinationErrorCode, Boolean> ownsPartition(
      final ConsumerGroup group, final String memberId, final int partitionId) {
    if (group.isAssignedPartition(memberId, partitionId)) {
      return Either.right(true);
    }
    return Either.left(NOT_PARTITION_OWNER);
  }
}
