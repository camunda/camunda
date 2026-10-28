/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class CopiedRecordTest {

  @Test
  void shouldKeepAuthorizationsOfCollectedRecordWhenMetadataIsReused() {
    // given
    final var metadata = new RecordMetadata();
    final var firstMetadata = encodedMetadata("first-user");
    metadata.wrap(firstMetadata, 0, firstMetadata.capacity());
    final var collectedRecord =
        new CopiedRecord<>(new SignalRecord().setSignalName("signal"), metadata, 1L, 1, 1L, -1L, 1L)
            .copyOf();

    // when
    final var otherMetadata = encodedMetadata("second-user");
    metadata.wrap(otherMetadata, 0, otherMetadata.capacity());

    // then
    assertThat(collectedRecord.getAuthorizations())
        .isEqualTo(Map.of(AUTHORIZED_USERNAME, "first-user"));
  }

  private static DirectBuffer encodedMetadata(final String username) {
    final var metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .valueType(ValueType.SIGNAL)
            .intent(SignalIntent.BROADCAST)
            .authorization(new AuthInfo().setClaims(Map.of(AUTHORIZED_USERNAME, username)));
    final var buffer = new UnsafeBuffer(new byte[metadata.getLength()]);
    metadata.write(buffer, 0);
    return buffer;
  }
}
