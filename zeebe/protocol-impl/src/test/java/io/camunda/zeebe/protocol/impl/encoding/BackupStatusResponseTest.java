/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.management.BackupStatusCode;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class BackupStatusResponseTest {

  @Test
  void shouldEncodeAndDecodeZoneAwareBrokerId() {
    // given
    final var response =
        new BackupStatusResponse()
            .setBackupId(1)
            .setStatus(BackupStatusCode.COMPLETED)
            .setBrokerId(1, "zone-a")
            .setPartitionId(2)
            .setSnapshotId("sid")
            .setCreatedAt("2024-01-01T00:00:00Z")
            .setLastUpdated("2024-01-01T00:00:00Z");

    // when
    final var buffer = new UnsafeBuffer(new byte[response.getLength()]);
    response.write(buffer, 0);

    final var decoded = new BackupStatusResponse();
    decoded.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(decoded.getBrokerId()).isEqualTo(1);
    assertThat(decoded.getBrokerIdString()).isEqualTo("zone-a/1");
    assertThat(decoded.hasBrokerId()).isTrue();
  }
}
