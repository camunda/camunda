/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request.coordination;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * Guards decode-compatibility for the {@code cleanupPolicy} field added to {@link
 * CreateTopicRequest} (event-bridge ADR 0001, decision 2): a request encoded before the field
 * existed must still decode, defaulting to {@link CleanupPolicy#DELETE} — today's behavior. Mirrors
 * the {@code SchemaEvolution} pattern in {@code UnpackedObjectTest}: a stand-in for the pre-change
 * shape (just {@code name}/{@code partitionCount}/{@code replicationFactor}) is encoded, then
 * decoded with the current, 4-property {@link CreateTopicRequest}.
 */
final class CreateTopicRequestCompatibilityTest {

  @Test
  void shouldDefaultCleanupPolicyToDeleteWhenDecodingARequestWithoutTheField() {
    // given — a request encoded in the pre-cleanupPolicy shape
    final var legacyRequest = new LegacyCreateTopicRequest();
    legacyRequest.name.setValue("orders");
    legacyRequest.partitionCount.setValue(6);
    legacyRequest.replicationFactor.setValue(3);

    final var buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
    final int length = legacyRequest.write(buffer, 0);

    // when — decoded with the current request type
    final var request = new CreateTopicRequest();
    request.wrap(buffer, 0, length);

    // then — the fields that existed before decode unchanged, and the new field defaults
    assertThat(request.getName()).isEqualTo("orders");
    assertThat(request.getPartitionCount()).isEqualTo(6);
    assertThat(request.getReplicationFactor()).isEqualTo(3);
    assertThat(request.getCleanupPolicy()).isEqualTo(CleanupPolicy.DELETE);
  }

  /** Stand-in for {@link CreateTopicRequest} before the {@code cleanupPolicy} property existed. */
  private static final class LegacyCreateTopicRequest extends UnpackedObject {
    private final StringProperty name = new StringProperty("name", "");
    private final IntegerProperty partitionCount = new IntegerProperty("partitionCount", 0);
    private final IntegerProperty replicationFactor = new IntegerProperty("replicationFactor", 0);

    private LegacyCreateTopicRequest() {
      super(3);
      declareProperty(name).declareProperty(partitionCount).declareProperty(replicationFactor);
    }
  }
}
