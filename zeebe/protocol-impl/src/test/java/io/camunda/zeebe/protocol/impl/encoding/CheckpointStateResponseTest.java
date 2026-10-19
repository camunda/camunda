/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionLogArchiveState;
import io.camunda.zeebe.protocol.management.CheckpointStateResponseEncoder;
import io.camunda.zeebe.protocol.management.CheckpointStateResponseEncoder.LogArchiveStatesEncoder;
import io.camunda.zeebe.protocol.management.MessageHeaderEncoder;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class CheckpointStateResponseTest {

  @Test
  void shouldEncodeAndDecodeAllStates() {
    // given
    final var response = new CheckpointStateResponse();
    response.setCheckpointStates(
        Set.of(new PartitionCheckpointState(1, 10, CheckpointType.MARKER, 100, 1000)));
    response.setBackupStates(
        Set.of(new PartitionCheckpointState(1, 9, CheckpointType.HANDOVER, 90, 900, 5)));
    response.setLogArchiveStates(
        Set.of(
            new PartitionLogArchiveState(1, true, 1234, 800, 9, 1),
            new PartitionLogArchiveState(2, false, -1, -1, -1)));

    // when
    final var decoded = roundTrip(response);

    // then
    assertThat(decoded.getCheckpointStates()).isEqualTo(response.getCheckpointStates());
    assertThat(decoded.getBackupStates()).isEqualTo(response.getBackupStates());
    assertThat(decoded.getLogArchiveStates()).isEqualTo(response.getLogArchiveStates());
  }

  @Test
  void shouldWriteEmptyLogArchiveGroup() {
    // given
    final var response = new CheckpointStateResponse();

    // when
    final var decoded = roundTrip(response);

    // then
    assertThat(decoded.getLogArchiveStates()).isEmpty();
    assertThat(response.getLength())
        .isEqualTo(
            MessageHeaderEncoder.ENCODED_LENGTH
                + 3 * LogArchiveStatesEncoder.sbeHeaderSize()
                + new CheckpointStateResponseEncoder().sbeBlockLength());
  }

  @Test
  void shouldDecodeMessageOfSchemaVersionTwo() {
    // given - a message as written by a broker before the log archive states existed: schema
    // version 2 and no third group
    final var response = new CheckpointStateResponse();
    response.setCheckpointStates(
        Set.of(new PartitionCheckpointState(1, 10, CheckpointType.MARKER, 100, 1000)));
    response.setBackupStates(
        Set.of(new PartitionCheckpointState(1, 9, CheckpointType.SCHEDULED_BACKUP, 90, 900, 5)));
    final var buffer = new UnsafeBuffer(new byte[response.getLength()]);
    response.write(buffer, 0);
    new MessageHeaderEncoder().wrap(buffer, 0).version(2);
    final int versionTwoLength = buffer.capacity() - LogArchiveStatesEncoder.sbeHeaderSize();

    // when
    final var decoded = new CheckpointStateResponse();
    decoded.wrap(buffer, 0, versionTwoLength);

    // then
    assertThat(decoded.getCheckpointStates()).isEqualTo(response.getCheckpointStates());
    assertThat(decoded.getBackupStates()).isEqualTo(response.getBackupStates());
    assertThat(decoded.getLogArchiveStates()).isEmpty();
  }

  private static CheckpointStateResponse roundTrip(final CheckpointStateResponse response) {
    final var buffer = new UnsafeBuffer(new byte[response.getLength()]);
    final int written = response.write(buffer, 0);
    assertThat(written).isEqualTo(response.getLength());
    final var decoded = new CheckpointStateResponse();
    decoded.wrap(buffer, 0, buffer.capacity());
    return decoded;
  }
}
