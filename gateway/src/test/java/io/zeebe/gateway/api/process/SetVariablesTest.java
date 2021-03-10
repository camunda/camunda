/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.api.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesResponse;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.MsgPackUtil;
import java.util.Collections;
import org.junit.Test;

public final class SetVariablesTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final SetVariablesStub stub = new SetVariablesStub();
    stub.registerWith(brokerClient);

    final String variables = JsonUtil.toJson(Collections.singletonMap("key", "value"));

    final int partitionId = 1;
    final long elementInstanceKey = Protocol.encodePartitionId(partitionId, 1);
    final SetVariablesRequest request =
        SetVariablesRequest.newBuilder()
            .setElementInstanceKey(elementInstanceKey)
            .setVariables(variables)
            .build();

    // when
    final SetVariablesResponse response = client.setVariables(request);

    // then
    assertThat(response).isNotNull();
    assertThat(response.getKey()).isEqualTo(stub.getKey());

    final BrokerSetVariablesRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getKey()).isEqualTo(-1);
    assertThat(brokerRequest.getIntent()).isEqualTo(VariableDocumentIntent.UPDATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.VARIABLE_DOCUMENT);
    assertThat(brokerRequest.getPartitionId()).isEqualTo(partitionId);

    final VariableDocumentRecord brokerRequestValue = brokerRequest.getRequestWriter();
    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getVariablesBuffer(), variables);
    assertThat(brokerRequestValue.getScopeKey()).isEqualTo(elementInstanceKey);
  }
}
