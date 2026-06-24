/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.consumergroups.stream;

import io.camunda.eventbridge.protocol.request.coordination.CoordinationErrorCode;

/**
 * Fences an offset commit against the replicated group metadata — the single place the {@link
 * OffsetCommitProcessor} decides accept-or-reject, mirroring how the Zeebe engine keeps validation
 * in dedicated checkers rather than inline. Returns {@link CoordinationErrorCode#NONE} when the
 * commit may proceed, or the rejection code: the member must exist, present a current epoch (a
 * stale epoch is a zombie that lost its partitions), and own the partition.
 */
final class OffsetCommitValidator {

  private final DbGroupMetadataState groupMetadataState;

  OffsetCommitValidator(final DbGroupMetadataState groupMetadataState) {
    this.groupMetadataState = groupMetadataState;
  }

  CoordinationErrorCode validate(final OffsetCommitRecord command) {
    final var groupId = command.getGroupId();
    if (groupId == null || groupId.isEmpty()) {
      return CoordinationErrorCode.INVALID_GROUP_ID;
    }

    final var payload = groupMetadataState.get(groupId);
    if (payload == null) {
      return CoordinationErrorCode.UNKNOWN_MEMBER_ID;
    }

    final var member =
        GroupMetadataCodec.decode(payload).members().stream()
            .filter(m -> m.memberId().equals(command.getMemberId()))
            .findFirst()
            .orElse(null);
    if (member == null) {
      return CoordinationErrorCode.UNKNOWN_MEMBER_ID;
    }

    final long expectedEpoch = member.memberEpoch();
    if (expectedEpoch > command.getMemberEpoch()) {
      return CoordinationErrorCode.FENCED_MEMBER_EPOCH;
    }
    if (expectedEpoch != command.getMemberEpoch()) {
      return CoordinationErrorCode.UNKNOWN_MEMBER_ID;
    }

    if (!member.partitions().contains(command.getPartitionId())) {
      return CoordinationErrorCode.NOT_PARTITION_OWNER;
    }

    return CoordinationErrorCode.NONE;
  }
}
