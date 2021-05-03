/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.api.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import org.junit.Test;

public final class CreateProcessInstanceTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final CreateProcessInstanceStub stub = new CreateProcessInstanceStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceRequest request =
        CreateProcessInstanceRequest.newBuilder()
            .setProcessDefinitionKey(stub.getProcessDefinitionKey())
            .build();

    // when
    final CreateProcessInstanceResponse response = client.createProcessInstance(request);

    // then
    assertThat(response.getBpmnProcessId()).isEqualTo(stub.getProcessId());
    assertThat(response.getVersion()).isEqualTo(stub.getProcessVersion());
    assertThat(response.getProcessDefinitionKey()).isEqualTo(stub.getProcessDefinitionKey());
    assertThat(response.getProcessInstanceKey()).isEqualTo(stub.getProcessInstanceKey());

    final BrokerCreateProcessInstanceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(ProcessInstanceCreationIntent.CREATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_CREATION);

    final ProcessInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getProcessDefinitionKey())
        .isEqualTo(stub.getProcessDefinitionKey());
  }
}
