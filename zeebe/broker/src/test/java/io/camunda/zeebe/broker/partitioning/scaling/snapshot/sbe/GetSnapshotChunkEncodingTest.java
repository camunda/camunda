/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.GetSnapshotChunk;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class GetSnapshotChunkEncodingTest {

  @ParameterizedTest
  @ValueSource(strings = {"", "a", "abc"})
  public void shouldSerializeAndDeserializeGetSnapshotChunkRequest(final String lastChunkName) {
    final Optional<String> optionalChunkName =
        lastChunkName.isEmpty() ? Optional.empty() : Optional.of(lastChunkName);
    final var request =
        new GetSnapshotChunk(23, UUID.randomUUID(), optionalChunkName, optionalChunkName);
    final var serializer = new GetSnapshotChunkSerializer();
    final var deserializer = new GetSnapshotChunkDeserializer();
    final var buffer = ByteBuffer.allocate(4096);

    // when
    final var bytesWritten = serializer.serialize(request, new UnsafeBuffer(buffer), 123);
    final var deserialized = deserializer.deserialize(new UnsafeBuffer(buffer), 123, bytesWritten);

    // then
    assertThat(deserialized).isEqualTo(request);
  }
}
