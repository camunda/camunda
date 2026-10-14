/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.ticktock;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Sent by a follower to the system partition leader when it applied a tick, so the leader commits
 * that the follower saw it with its next tick.
 *
 * @param memberId the member id of the follower
 * @param tickId the id of the tick the follower applied
 */
record Tock(String memberId, long tickId) {
  static final String SUBJECT = "system-partition-tock";

  byte[] encode() {
    final var memberIdBytes = memberId.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + memberIdBytes.length)
        .putLong(tickId)
        .put(memberIdBytes)
        .array();
  }

  static Tock decode(final byte[] bytes) {
    final var buffer = ByteBuffer.wrap(bytes);
    final var tickId = buffer.getLong();
    final var memberIdBytes = new byte[buffer.remaining()];
    buffer.get(memberIdBytes);
    return new Tock(new String(memberIdBytes, StandardCharsets.UTF_8), tickId);
  }
}
