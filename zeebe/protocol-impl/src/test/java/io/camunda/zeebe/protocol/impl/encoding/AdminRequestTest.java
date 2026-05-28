/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.management.AdminRequestType;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class AdminRequestTest {

  @Test
  void shouldEncodeAndDecodeZoneAwareBrokerId() {
    // given
    final var request = new AdminRequest();
    request.setBrokerId(1, "zone-a");
    request.setPartitionId(3);
    request.setType(AdminRequestType.PAUSE_EXPORTING);
    request.setPayload(new byte[] {1, 2, 3});

    // when
    final var buffer = new UnsafeBuffer(new byte[request.getLength()]);
    request.write(buffer, 0);

    final var decoded = new AdminRequest();
    decoded.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(decoded.getBrokerId()).isEqualTo(1);
    assertThat(decoded.getBrokerIdString()).isEqualTo("zone-a_1");
  }
}
