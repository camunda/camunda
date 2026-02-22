/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
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
            .addAllTags(stub.getTags())
            .build();

    // when
    final CreateProcessInstanceResponse response = client.createProcessInstance(request);

    // then
    assertThat(response.getBpmnProcessId()).isEqualTo(stub.getProcessId());
    assertThat(response.getVersion()).isEqualTo(stub.getProcessVersion());
    assertThat(response.getProcessDefinitionKey()).isEqualTo(stub.getProcessDefinitionKey());
    assertThat(response.getProcessInstanceKey()).isEqualTo(stub.getProcessInstanceKey());
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(response.getTagsList()).containsAll(stub.getTags());
    assertThat(response.getBusinessId()).isEmpty();

    final BrokerCreateProcessInstanceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(ProcessInstanceCreationIntent.CREATE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_CREATION);

    final ProcessInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getProcessDefinitionKey())
        .isEqualTo(stub.getProcessDefinitionKey());
    assertThat(brokerRequestValue.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(brokerRequestValue.getTags()).isEqualTo(stub.getTags());
  }

  @Test
  public void shouldMapRequestAndResponseWithBusinessId() {
    // given
    final CreateProcessInstanceStub stub = new CreateProcessInstanceStub();
    stub.registerWith(brokerClient);
    final String businessId = "order-12345";

    final CreateProcessInstanceRequest request =
        CreateProcessInstanceRequest.newBuilder()
            .setProcessDefinitionKey(stub.getProcessDefinitionKey())
            .setBusinessId(businessId)
            .build();

    // when
    final CreateProcessInstanceResponse response = client.createProcessInstance(request);

    // then
    assertThat(response.getProcessInstanceKey()).isEqualTo(stub.getProcessInstanceKey());
    assertThat(response.getBusinessId()).isEqualTo(businessId);

    final BrokerCreateProcessInstanceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    final ProcessInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getBusinessId()).isEqualTo(businessId);
  }
}
