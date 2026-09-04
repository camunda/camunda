/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RuntimeVariablesIntent;
import io.camunda.zeebe.protocol.record.value.RuntimeVariableScope;
import org.junit.jupiter.api.Test;

final class BrokerFetchRuntimeVariablesRequestTest {

  @Test
  void shouldRouteToScopePartition() {
    // given
    final var scopeKey = Protocol.encodePartitionId(3, 42L);

    // when
    final var request =
        new BrokerFetchRuntimeVariablesRequest()
            .setScopeKey(scopeKey)
            .setScope(RuntimeVariableScope.LOCAL);

    // then
    assertThat(request.getValueType()).isEqualTo(ValueType.RUNTIME_VARIABLES);
    assertThat(request.getIntent()).isEqualTo(RuntimeVariablesIntent.FETCH);
    assertThat(request.getPartitionId()).isEqualTo(3);
    assertThat(request.getRequestWriter().getScopeKey()).isEqualTo(scopeKey);
    assertThat(request.getRequestWriter().getScope()).isEqualTo(RuntimeVariableScope.LOCAL);
  }
}
