/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse.BackupStatus;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.time.Instant;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class BackupListResponseTest {

  @Test
  void shouldEncodeAndDecodeBackupListResponse() {
    // given
    final List<BackupStatus> backups =
        List.of(
            new BackupStatus(
                1, 1, BackupStatusCode.COMPLETED, "", "8.1.1", Instant.now().toString()),
            new BackupStatus(
                1, 1, BackupStatusCode.FAILED, "ERROR", "8.1.2", Instant.now().toString()));

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
