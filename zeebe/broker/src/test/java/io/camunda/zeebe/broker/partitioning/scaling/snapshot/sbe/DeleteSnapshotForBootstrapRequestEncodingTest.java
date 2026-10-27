/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot.sbe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.DeleteSnapshotForBootstrapRequest;
import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class DeleteSnapshotForBootstrapRequestEncodingTest {

  @ParameterizedTest
  @ValueSource(ints = {1, 23, 100, 999})
  public void shouldSerializeAndDeserializeDeleteSnapshotForBootstrapRequest(
      final int partitionId) {
    final var request = new DeleteSnapshotForBootstrapRequest(partitionId);
    final var serializer = new DeleteSnapshotForBootstrapRequestSerializer();
    final var deserializer = new DeleteSnapshotForBootstrapRequestDeserializer();
    final var buffer = ByteBuffer.allocate(4096);

    // when
    final var bytesWritten = serializer.serialize(request, new UnsafeBuffer(buffer), 123);
    final var deserialized = deserializer.deserialize(new UnsafeBuffer(buffer), 123, bytesWritten);

    // then
    assertThat(deserialized).isEqualTo(request);
  }
}
