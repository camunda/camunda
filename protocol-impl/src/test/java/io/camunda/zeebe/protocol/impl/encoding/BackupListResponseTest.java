/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse.BackupStatus;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class BackupListResponseTest {

  @Test
  void shouldEncodeAndDecodeBackupListResponse() {
    // given
    final List<BackupStatus> backups = new ArrayList<>();
    backups.add(
        new BackupStatus()
            .setBackupId(1)
            .setPartitionId(1)
            .setStatus(BackupStatusCode.COMPLETED)
            .setBrokerVersion("8.1.1")
            .setFailureReason("")
            .setCreatedAt(Instant.now().toString()));
    backups.add(
        new BackupStatus()
            .setBackupId(2)
            .setPartitionId(1)
            .setStatus(BackupStatusCode.FAILED)
            .setBrokerVersion("8.1.2")
            .setFailureReason("Error")
            .setCreatedAt(Instant.now().toString()));
    final BackupListResponse toEncode = new BackupListResponse(backups);

    // when
    final byte[] bytes = new byte[toEncode.getLength()];
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);
    toEncode.write(buffer, 0);

    // then
    final BackupListResponse decoded = new BackupListResponse(buffer, 0, buffer.capacity());

    assertThat(decoded.getBackups()).containsExactlyInAnyOrderElementsOf(toEncode.getBackups());
  }
}
