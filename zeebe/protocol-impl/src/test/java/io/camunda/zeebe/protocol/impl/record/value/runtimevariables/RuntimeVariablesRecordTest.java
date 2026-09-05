/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.runtimevariables;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.RuntimeVariableScope;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class RuntimeVariablesRecordTest {

  @Test
  void shouldRoundTripFieldsViaMsgPack() {
    // given
    final var original =
        new RuntimeVariablesRecord()
            .setScopeKey(123L)
            .setScope(RuntimeVariableScope.LOCAL)
            .setTenantId("tenant-a")
            .setVariables(
                new UnsafeBuffer(MsgPackConverter.convertToMsgPack(Map.of("foo", "bar"))));

    // when
    final var copy = new RuntimeVariablesRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getScopeKey()).isEqualTo(123L);
    assertThat(copy.getScope()).isEqualTo(RuntimeVariableScope.LOCAL);
    assertThat(copy.getTenantId()).isEqualTo("tenant-a");
    assertThat(copy.getVariables()).containsExactly(Map.entry("foo", "bar"));
  }

  @Test
  void shouldDefaultScopeToEffective() {
    assertThat(new RuntimeVariablesRecord().getScope()).isEqualTo(RuntimeVariableScope.EFFECTIVE);
  }
}
