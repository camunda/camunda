/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.process;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceWithResultResponse;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.HashSet;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;

public final class CreateProcessInstanceWithResultTest extends GatewayTest {

  @Test
  public void shouldMapToBrokerRequestWithDefaultTenant() {
    // given
    final CreateProcessInstanceWithResultStub stub = new CreateProcessInstanceWithResultStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceWithResultRequest request =
        CreateProcessInstanceWithResultRequest.newBuilder()
            .setRequest(
                CreateProcessInstanceRequest.newBuilder()
                    .setProcessDefinitionKey(stub.getProcessDefinitionKey())
                    .addAllTags(stub.getTags()))
            .addAllFetchVariables(List.of("x"))
            .build();

    // when
    client.createProcessInstanceWithResult(request);

    // then
    final BrokerCreateProcessInstanceWithResultRequest brokerRequest =
        brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent())
        .isEqualTo(ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.PROCESS_INSTANCE_CREATION);

    final ProcessInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getProcessDefinitionKey())
        .isEqualTo(stub.getProcessDefinitionKey());
    assertThat(brokerRequestValue.fetchVariables().iterator().next().getValue())
        .isEqualTo(wrapString("x"));
    assertThat(brokerRequestValue.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(brokerRequestValue.getTags()).isEqualTo(stub.getTags());
  }

  @Test
  public void shouldMapRequestAndResponseWithDefaultTenant() {
    // given
    final CreateProcessInstanceWithResultStub stub = new CreateProcessInstanceWithResultStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceWithResultRequest request =
        CreateProcessInstanceWithResultRequest.newBuilder()
            .setRequest(
                CreateProcessInstanceRequest.newBuilder()
                    .setProcessDefinitionKey(stub.getProcessDefinitionKey())
                    .addAllTags(stub.getTags()))
            .build();

    // when
    final CreateProcessInstanceWithResultResponse response =
        client.createProcessInstanceWithResult(request);

    // then
    assertThat(response.getBpmnProcessId()).isEqualTo(stub.getProcessId());
    assertThat(response.getVersion()).isEqualTo(stub.getProcessVersion());
    assertThat(response.getProcessDefinitionKey()).isEqualTo(stub.getProcessDefinitionKey());
    assertThat(response.getProcessInstanceKey()).isEqualTo(stub.getProcessInstanceKey());
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(new HashSet(response.getTagsList())).isEqualTo(stub.getTags());
    assertThat(response.getBusinessId()).isEmpty();
  }

  @Test
  public void shouldMapRequestAndResponseWithBusinessId() {
    // given
    final CreateProcessInstanceWithResultStub stub = new CreateProcessInstanceWithResultStub();
    stub.registerWith(brokerClient);
    final String businessId = "order-12345";

    final CreateProcessInstanceWithResultRequest request =
        CreateProcessInstanceWithResultRequest.newBuilder()
            .setRequest(
                CreateProcessInstanceRequest.newBuilder()
                    .setProcessDefinitionKey(stub.getProcessDefinitionKey())
                    .setBusinessId(businessId))
            .build();

    // when
    final CreateProcessInstanceWithResultResponse response =
        client.createProcessInstanceWithResult(request);

    // then
    assertThat(response.getProcessInstanceKey()).isEqualTo(stub.getProcessInstanceKey());
    assertThat(response.getBusinessId()).isEqualTo(businessId);

    final ProcessInstanceCreationRecord brokerRequestValue =
        brokerClient
            .<BrokerCreateProcessInstanceWithResultRequest>getSingleBrokerRequest()
            .getRequestWriter();
    assertThat(brokerRequestValue.getBusinessId()).isEqualTo(businessId);
  }

  @Test
  @Ignore("https://github.com/camunda/camunda/issues/14041")
  public void shouldMapRequestAndResponseWithCustomTenant() {
    // given
    final String tenantId = "test-tenant";
    final CreateProcessInstanceWithResultStub stub = new CreateProcessInstanceWithResultStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceWithResultRequest request =
        CreateProcessInstanceWithResultRequest.newBuilder()
            .setRequest(
                CreateProcessInstanceRequest.newBuilder()
                    .setProcessDefinitionKey(stub.getProcessDefinitionKey())
                    .setTenantId(tenantId))
            .build();

    // when
    final CreateProcessInstanceWithResultResponse response =
        client.createProcessInstanceWithResult(request);

    // then
    assertThat(response.getTenantId()).isEqualTo(tenantId);

    final ProcessInstanceCreationRecord brokerRequestValue =
        (ProcessInstanceCreationRecord) brokerClient.getSingleBrokerRequest().getRequestWriter();
    assertThat(brokerRequestValue.getTenantId()).isEqualTo(tenantId);
  }
}
