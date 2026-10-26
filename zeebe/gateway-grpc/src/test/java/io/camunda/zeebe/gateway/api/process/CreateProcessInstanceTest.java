/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.VariableMetadata;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
    assertThat(response.hasBusinessId()).isFalse();

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
    assertThat(brokerRequestValue.hasBusinessId()).isTrue();
    assertThat(brokerRequestValue.getBusinessId()).isEqualTo(businessId);
  }

  @Test
  public void shouldMapVersionTagToBrokerRequest() {
    // given
    final CreateProcessInstanceStub stub = new CreateProcessInstanceStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceRequest request =
        CreateProcessInstanceRequest.newBuilder()
            .setBpmnProcessId(stub.getProcessId())
            .setVersionTag("v1.2")
            .build();

    // when
    client.createProcessInstance(request);

    // then
    final BrokerCreateProcessInstanceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    final ProcessInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getVersionTag()).isEqualTo("v1.2");
    assertThat(brokerRequestValue.getVersion()).isEqualTo(-1);
  }

  @Test
  public void shouldForwardExplicitVersionUnchangedWithVersionTag() {
    // given
    final CreateProcessInstanceStub stub = new CreateProcessInstanceStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceRequest request =
        CreateProcessInstanceRequest.newBuilder()
            .setBpmnProcessId(stub.getProcessId())
            .setVersion(3)
            .setVersionTag("v1.2")
            .build();

    // when
    client.createProcessInstance(request);

    // then
    final BrokerCreateProcessInstanceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    final ProcessInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getVersion()).isEqualTo(3);
  }

  @Test
  public void shouldRejectBlankVersionTag() {
    // given
    final CreateProcessInstanceRequest request =
        CreateProcessInstanceRequest.newBuilder()
            .setBpmnProcessId("process")
            .setVersionTag("  ")
            .build();

    // when/then
    assertThatThrownBy(() -> client.createProcessInstance(request))
        .isInstanceOf(StatusRuntimeException.class)
        .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  public void shouldLeaveVersionTagEmptyWhenNotSet() {
    // given
    final CreateProcessInstanceStub stub = new CreateProcessInstanceStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceRequest request =
        CreateProcessInstanceRequest.newBuilder()
            .setProcessDefinitionKey(stub.getProcessDefinitionKey())
            .build();

    // when
    client.createProcessInstance(request);

    // then
    final BrokerCreateProcessInstanceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    final ProcessInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getVersionTag()).isEmpty();
  }

  @Test
  public void shouldMapDuplicateBusinessIdRejectionToAlreadyExists() {
    // given
    final var businessId = "order-12345";
    final var processDefinitionId = "bpmnProcessId";
    final var rejectionReason =
        "Expected to create instance of process with business id '%s', but an instance with this business id already exists for process definition '%s'"
            .formatted(businessId, processDefinitionId);
    final CreateProcessInstanceStub stub = new CreateProcessInstanceStub();
    stub.respondWith(
            new BrokerRejectionResponse<>(
                new BrokerRejection(
                    ProcessInstanceCreationIntent.CREATE,
                    1L,
                    RejectionType.ALREADY_EXISTS,
                    rejectionReason)))
        .registerWith(brokerClient);

    final CreateProcessInstanceRequest request =
        CreateProcessInstanceRequest.newBuilder()
            .setProcessDefinitionKey(stub.getProcessDefinitionKey())
            .setBusinessId(businessId)
            .build();

    // when / then
    assertThatThrownBy(() -> client.createProcessInstance(request))
        .isInstanceOf(StatusRuntimeException.class)
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.THROWABLE)
        .extracting(
            t -> ((StatusRuntimeException) t).getStatus().getCode(),
            t -> ((StatusRuntimeException) t).getStatus().getDescription())
        .containsExactly(
            Status.Code.ALREADY_EXISTS,
            "Command 'CREATE' rejected with code 'ALREADY_EXISTS': " + rejectionReason);
  }

  @Test
  public void shouldMapVariablesMetadata() {
    // given
    final CreateProcessInstanceStub stub = new CreateProcessInstanceStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceRequest request =
        CreateProcessInstanceRequest.newBuilder()
            .setProcessDefinitionKey(stub.getProcessDefinitionKey())
            .setVariables("{\"ssn\":\"123\"}")
            .putVariablesMetadata(
                "ssn",
                VariableMetadata.newBuilder()
                    .addProtectionModes(VariableMetadata.ProtectionMode.REDACT)
                    .build())
            .build();

    // when
    final CreateProcessInstanceResponse response = client.createProcessInstance(request);

    // then
    assertThat(response.getProcessInstanceKey()).isEqualTo(stub.getProcessInstanceKey());
    final BrokerCreateProcessInstanceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    final ProcessInstanceCreationRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokerRequestValue.getVariablesMetadata()).containsOnlyKeys("ssn");
    assertThat(brokerRequestValue.getVariablesMetadata().get("ssn").getProtectionModes())
        .containsExactly(ProtectionMode.REDACT);
  }

  @Test
  public void shouldRejectUnrecognizedProtectionMode() {
    // given
    final CreateProcessInstanceStub stub = new CreateProcessInstanceStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceRequest request =
        CreateProcessInstanceRequest.newBuilder()
            .setProcessDefinitionKey(stub.getProcessDefinitionKey())
            .setVariables("{\"ssn\":\"123\"}")
            .putVariablesMetadata(
                "ssn", VariableMetadata.newBuilder().addProtectionModesValue(99).build())
            .build();

    // when / then
    assertThatThrownBy(() -> client.createProcessInstance(request))
        .isInstanceOf(StatusRuntimeException.class)
        .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  public void shouldRejectVariablesMetadataOfUnknownVariable() {
    // given
    final CreateProcessInstanceStub stub = new CreateProcessInstanceStub();
    stub.registerWith(brokerClient);

    final CreateProcessInstanceRequest request =
        CreateProcessInstanceRequest.newBuilder()
            .setProcessDefinitionKey(stub.getProcessDefinitionKey())
            .setVariables("{\"name\":\"x\"}")
            .putVariablesMetadata(
                "ssn",
                VariableMetadata.newBuilder()
                    .addProtectionModes(VariableMetadata.ProtectionMode.REDACT)
                    .build())
            .build();

    // when / then
    assertThatThrownBy(() -> client.createProcessInstance(request))
        .isInstanceOf(StatusRuntimeException.class)
        .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
        .isEqualTo(Status.Code.INVALID_ARGUMENT);
  }
}
