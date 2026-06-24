/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class ClusterVariableRecordTest {

  @Test
  void shouldRoundTripFieldsViaMsgPack() {
    // given
    final Map<String, Object> metadata = Map.of("credentialType", "OAUTH2", "version", "1");
    final var original =
        new ClusterVariableRecord()
            .setName("myVar")
            .setScope(ClusterVariableScope.GLOBAL)
            .setTenantId("<default>")
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"value\"")))
            .setMetadata(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(metadata)));

    // when
    final var copy = new ClusterVariableRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getName()).isEqualTo(original.getName());
    assertThat(copy.getScope()).isEqualTo(original.getScope());
    assertThat(copy.getTenantId()).isEqualTo(original.getTenantId());
    assertThat(copy.getValue()).isEqualTo(original.getValue());
    assertThat(copy.getMetadata()).containsExactlyInAnyOrderEntriesOf(metadata);
  }

  @Test
  void shouldReturnEmptyMetadataIfNotSet() {
    // given
    final var original =
        new ClusterVariableRecord()
            .setName("myVar")
            .setScope(ClusterVariableScope.GLOBAL)
            .setTenantId("<default>")
            .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"value\"")));

    // when
    final var copy = new ClusterVariableRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getName()).isEqualTo(original.getName());
    assertThat(copy.getScope()).isEqualTo(original.getScope());
    assertThat(copy.getTenantId()).isEqualTo(original.getTenantId());
    assertThat(copy.getValue()).isEqualTo(original.getValue());
    assertThat(copy.getMetadata()).isEmpty();
  }
}
