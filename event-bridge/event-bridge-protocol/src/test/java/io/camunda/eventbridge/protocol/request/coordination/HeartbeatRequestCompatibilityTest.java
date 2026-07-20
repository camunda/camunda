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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Guards decode-compatibility for the {@code standbyReadinessPartitions}/{@code
 * standbyReadinessLag} fields added to {@link HeartbeatRequest} (event-bridge-streaming ADR 0009 /
 * consumer-groups ADR 0006, decision 1): a request encoded before standby support existed must
 * still decode, defaulting to no reported readiness — today's behavior for every member. Mirrors
 * {@code CreateTopicRequestCompatibilityTest}.
 */
final class HeartbeatRequestCompatibilityTest {

  @Test
  void shouldDefaultStandbyReadinessToEmptyWhenDecodingARequestWithoutTheField() {
    // given — a request encoded in the pre-standby shape
    final var legacyRequest = new LegacyHeartbeatRequest();
    legacyRequest.groupId.setValue("orders-group");
    legacyRequest.memberId.setValue("member-1");
    legacyRequest.memberEpoch.setValue(3L);
    legacyRequest.ownedPartitions.add().setTopic("orders").setPartition(0);

    final var buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
    final int length = legacyRequest.write(buffer, 0);

    // when — decoded with the current request type
    final var request = new HeartbeatRequest();
    request.wrap(buffer, 0, length);

    // then — the fields that existed before decode unchanged, and the new field defaults to empty
    assertThat(request.getGroupId()).isEqualTo("orders-group");
    assertThat(request.getMemberId()).isEqualTo("member-1");
    assertThat(request.getMemberEpoch()).isEqualTo(3L);
    assertThat(request.getOwnedPartitions()).hasSize(1);
    assertThat(request.getStandbyReadiness()).isEmpty();
  }

  /** Stand-in for {@link HeartbeatRequest} before the standby-readiness properties existed. */
  private static final class LegacyHeartbeatRequest extends UnpackedObject {
    private final StringProperty groupId = new StringProperty("groupId", "");
    private final StringProperty memberId = new StringProperty("memberId", "");
    private final LongProperty memberEpoch = new LongProperty("memberEpoch", -1L);
    private final ArrayProperty<TopicPartitionValue> ownedPartitions =
        new ArrayProperty<>("ownedPartitions", TopicPartitionValue::new);

    private LegacyHeartbeatRequest() {
      super(4);
      declareProperty(groupId)
          .declareProperty(memberId)
          .declareProperty(memberEpoch)
          .declareProperty(ownedPartitions);
    }
  }
}
