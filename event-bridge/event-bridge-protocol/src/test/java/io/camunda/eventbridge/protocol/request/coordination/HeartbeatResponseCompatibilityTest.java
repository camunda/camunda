/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.protocol.topic.TopicPartitionValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Guards decode-compatibility for the {@code standbyAssignment} field added to {@link
 * HeartbeatResponse} (event-bridge-streaming ADR 0009 / consumer-groups ADR 0006, decision 1): a
 * response encoded before standby support existed must still decode, defaulting to no standby
 * target — today's behavior for every member. Mirrors {@code CreateTopicRequestCompatibilityTest}.
 */
final class HeartbeatResponseCompatibilityTest {

  @Test
  void shouldDefaultStandbyAssignmentToEmptyWhenDecodingAResponseWithoutTheField() {
    // given — a response encoded in the pre-standby shape
    final var legacyResponse = new LegacyHeartbeatResponse();
    legacyResponse.errorCode.setValue(CoordinationErrorCode.NONE);
    legacyResponse.memberId.setValue("member-1");
    legacyResponse.memberEpoch.setValue(3L);
    legacyResponse.assignment.add().setTopic("orders").setPartition(0);
    legacyResponse.assignmentEpoch.setValue(7L);

    final var buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
    final int length = legacyResponse.write(buffer, 0);

    // when — decoded with the current response type
    final var response = new HeartbeatResponse();
    response.wrap(buffer, 0, length);

    // then — the fields that existed before decode unchanged, and the new field defaults to empty
    assertThat(response.getErrorCode()).isEqualTo(CoordinationErrorCode.NONE);
    assertThat(response.getMemberId()).isEqualTo("member-1");
    assertThat(response.getMemberEpoch()).isEqualTo(3L);
    assertThat(response.getAssignment()).hasSize(1);
    assertThat(response.getAssignmentEpoch()).isEqualTo(7L);
    assertThat(response.getStandbyAssignment()).isEmpty();
  }

  /** Stand-in for {@link HeartbeatResponse} before the {@code standbyAssignment} field existed. */
  private static final class LegacyHeartbeatResponse extends UnpackedObject {
    private final EnumProperty<CoordinationErrorCode> errorCode =
        new EnumProperty<>("errorCode", CoordinationErrorCode.class, CoordinationErrorCode.UNKNOWN);
    private final StringProperty memberId = new StringProperty("memberId", "");
    private final LongProperty memberEpoch = new LongProperty("memberEpoch", -1L);
    private final ArrayProperty<TopicPartitionValue> assign =
        new ArrayProperty<>("assign", TopicPartitionValue::new);
    private final ArrayProperty<TopicPartitionValue> revoke =
        new ArrayProperty<>("revoke", TopicPartitionValue::new);
    private final ArrayProperty<TopicPartitionValue> assignment =
        new ArrayProperty<>("assignment", TopicPartitionValue::new);
    private final LongProperty assignmentEpoch = new LongProperty("assignmentEpoch", -1L);
    private final ArrayProperty<TopicPartitionValue> committedPartitions =
        new ArrayProperty<>("committedPartitions", TopicPartitionValue::new);
    private final ArrayProperty<LongValue> committedOffsets =
        new ArrayProperty<>("committedOffsets", LongValue::new);

    private LegacyHeartbeatResponse() {
      super(9);
      declareProperty(errorCode)
          .declareProperty(memberId)
          .declareProperty(memberEpoch)
          .declareProperty(assign)
          .declareProperty(revoke)
          .declareProperty(assignment)
          .declareProperty(assignmentEpoch)
          .declareProperty(committedPartitions)
          .declareProperty(committedOffsets);
    }
  }
}
