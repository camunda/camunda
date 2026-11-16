/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.processdefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.gateway.ResponseMapper;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerResumeProcessDefinitionRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSuspendProcessDefinitionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResumeProcessDefinitionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResumeProcessDefinitionResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SuspendProcessDefinitionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SuspendProcessDefinitionResponse;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionResolvedVersion;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.List;
import org.junit.Test;

public final class ProcessDefinitionSuspensionTest extends GatewayTest {

  private static final long PROCESS_DEFINITION_KEY = 2251799813685249L;
  private static final long OTHER_PROCESS_DEFINITION_KEY = 2251799813685250L;
  private static final long BATCH_OPERATION_KEY = 2251799813685300L;
  private static final String BPMN_PROCESS_ID = "invoice";

  @Test
  public void shouldMapSuspensionByKeyAndResponse() {
    // given
    stubSuspension(
        suspensionRecord(
            BATCH_OPERATION_KEY,
            resolvedVersion(
                PROCESS_DEFINITION_KEY, 1, ProcessDefinitionSuspensionOutcome.SUSPENDED),
            resolvedVersion(
                OTHER_PROCESS_DEFINITION_KEY,
                2,
                ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED)));
    final var request =
        SuspendProcessDefinitionRequest.newBuilder()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setIncludeProcessInstances(true)
            .build();

    // when
    final SuspendProcessDefinitionResponse response = client.suspendProcessDefinition(request);

    // then
    final BrokerSuspendProcessDefinitionRequest brokerRequest =
        brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(ProcessDefinitionSuspensionIntent.SUSPEND);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.PROCESS_DEFINITION_SUSPENSION);
    final ProcessDefinitionSuspensionRecord value = brokerRequest.getRequestWriter();
    assertThat(value.getProcessDefinitionKey()).isEqualTo(PROCESS_DEFINITION_KEY);
    assertThat(value.getBpmnProcessId()).isEmpty();
    assertThat(value.getVersion()).isEqualTo(-1);
    assertThat(value.getTenantId()).isEmpty();
    assertThat(value.isIncludeProcessInstances()).isTrue();

    assertThat(response.getProcessDefinitionsList())
        .extracting(
            SuspendProcessDefinitionResponse.ProcessDefinitionVersion::getProcessDefinitionKey,
            SuspendProcessDefinitionResponse.ProcessDefinitionVersion::getVersion,
            SuspendProcessDefinitionResponse.ProcessDefinitionVersion::getOutcome)
        .containsExactly(
            tuple(
                PROCESS_DEFINITION_KEY,
                1,
                SuspendProcessDefinitionResponse.ProcessDefinitionVersion.Outcome.SUSPENDED),
            tuple(
                OTHER_PROCESS_DEFINITION_KEY,
                2,
                SuspendProcessDefinitionResponse.ProcessDefinitionVersion.Outcome
                    .ALREADY_SUSPENDED));
    assertThat(response.getBatchOperationKey()).isEqualTo(String.valueOf(BATCH_OPERATION_KEY));
  }

  @Test
  public void shouldMapSuspensionByIdWithVersionAndTenant() {
    // given
    stubSuspension(suspensionRecord(-1L));
    final var request =
        SuspendProcessDefinitionRequest.newBuilder()
            .setBpmnProcessId(BPMN_PROCESS_ID)
            .setVersion(3)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();

    // when
    client.suspendProcessDefinition(request);

    // then
    final BrokerSuspendProcessDefinitionRequest brokerRequest =
        brokerClient.getSingleBrokerRequest();
    final ProcessDefinitionSuspensionRecord value = brokerRequest.getRequestWriter();
    assertThat(value.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(value.getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(value.getVersion()).isEqualTo(3);
    assertThat(value.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(value.isIncludeProcessInstances()).isFalse();
  }

  @Test
  public void shouldSuspendAllVersionsWhenVersionIsOmitted() {
    // given
    stubSuspension(suspensionRecord(-1L));
    final var request =
        SuspendProcessDefinitionRequest.newBuilder().setBpmnProcessId(BPMN_PROCESS_ID).build();

    // when
    client.suspendProcessDefinition(request);

    // then
    final BrokerSuspendProcessDefinitionRequest brokerRequest =
        brokerClient.getSingleBrokerRequest();
    final ProcessDefinitionSuspensionRecord value = brokerRequest.getRequestWriter();
    assertThat(value.getVersion()).isEqualTo(-1);
  }

  @Test
  public void shouldLeaveBatchOperationKeyUnsetWithoutCascade() {
    // given
    stubSuspension(
        suspensionRecord(
            -1L,
            resolvedVersion(
                PROCESS_DEFINITION_KEY, 1, ProcessDefinitionSuspensionOutcome.SUSPENDED)));
    final var request =
        SuspendProcessDefinitionRequest.newBuilder()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .build();

    // when
    final var response = client.suspendProcessDefinition(request);

    // then
    assertThat(response.hasBatchOperationKey()).isFalse();
  }

  @Test
  public void shouldRejectSuspensionWithoutKeyAndBpmnProcessId() {
    // given
    final var request = SuspendProcessDefinitionRequest.getDefaultInstance();

    // when / then
    assertInvalidArgument(
        () -> client.suspendProcessDefinition(request),
        "Expected to SuspendProcessDefinition by either a process definition key or a BPMN process id, but neither was provided");
  }

  @Test
  public void shouldRejectSuspensionWithKeyAndBpmnProcessId() {
    // given
    final var request =
        SuspendProcessDefinitionRequest.newBuilder()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setBpmnProcessId(BPMN_PROCESS_ID)
            .build();

    // when / then
    assertInvalidArgument(
        () -> client.suspendProcessDefinition(request),
        "Expected to SuspendProcessDefinition by either a process definition key or a BPMN process id, but both were provided");
  }

  @Test
  public void shouldRejectSuspensionWithKeyAndVersion() {
    // given
    final var request =
        SuspendProcessDefinitionRequest.newBuilder()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setVersion(1)
            .build();

    // when / then
    assertInvalidArgument(
        () -> client.suspendProcessDefinition(request),
        "The version and tenant id can only be set together with a BPMN process id");
  }

  @Test
  public void shouldRejectSuspensionWithKeyAndTenantId() {
    // given
    final var request =
        SuspendProcessDefinitionRequest.newBuilder()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setTenantId("tenant-a")
            .build();

    // when / then
    assertInvalidArgument(
        () -> client.suspendProcessDefinition(request),
        "The version and tenant id can only be set together with a BPMN process id");
  }

  @Test
  public void shouldRejectSuspensionWithNonPositiveVersion() {
    // given
    final var request =
        SuspendProcessDefinitionRequest.newBuilder()
            .setBpmnProcessId(BPMN_PROCESS_ID)
            .setVersion(0)
            .build();

    // when / then
    assertInvalidArgument(
        () -> client.suspendProcessDefinition(request),
        "Expected to SuspendProcessDefinition with a positive version, but got '0'");
  }

  @Test
  public void shouldMapNotFoundRejectionOfSuspension() {
    // given
    final var reason = "Expected to suspend process definition 'invoice', but none was found";
    stubSuspension(suspensionRecord(-1L))
        .respondWith(
            new BrokerRejectionResponse<>(
                new BrokerRejection(
                    ProcessDefinitionSuspensionIntent.SUSPEND,
                    1L,
                    RejectionType.NOT_FOUND,
                    reason)));
    final var request =
        SuspendProcessDefinitionRequest.newBuilder().setBpmnProcessId(BPMN_PROCESS_ID).build();

    // when / then
    assertStatus(
        () -> client.suspendProcessDefinition(request),
        Status.Code.NOT_FOUND,
        "Command 'SUSPEND' rejected with code 'NOT_FOUND': " + reason);
  }

  @Test
  public void shouldMapInvalidStateRejectionOfSuspension() {
    // given
    final var reason = "Expected to suspend process definition, but it is being deleted";
    stubSuspension(suspensionRecord(-1L))
        .respondWith(
            new BrokerRejectionResponse<>(
                new BrokerRejection(
                    ProcessDefinitionSuspensionIntent.SUSPEND,
                    1L,
                    RejectionType.INVALID_STATE,
                    reason)));
    final var request =
        SuspendProcessDefinitionRequest.newBuilder()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .build();

    // when / then
    assertStatus(
        () -> client.suspendProcessDefinition(request),
        Status.Code.FAILED_PRECONDITION,
        "Command 'SUSPEND' rejected with code 'INVALID_STATE': " + reason);
  }

  @Test
  public void shouldMapResumptionByKeyAndResponse() {
    // given
    stubResumption(
        suspensionRecord(
            BATCH_OPERATION_KEY,
            resolvedVersion(PROCESS_DEFINITION_KEY, 1, ProcessDefinitionSuspensionOutcome.RESUMED),
            resolvedVersion(
                OTHER_PROCESS_DEFINITION_KEY,
                2,
                ProcessDefinitionSuspensionOutcome.ALREADY_RESUMED)));
    final var request =
        ResumeProcessDefinitionRequest.newBuilder()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setIncludeProcessInstances(true)
            .build();

    // when
    final ResumeProcessDefinitionResponse response = client.resumeProcessDefinition(request);

    // then
    final BrokerResumeProcessDefinitionRequest brokerRequest =
        brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(ProcessDefinitionSuspensionIntent.RESUME);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.PROCESS_DEFINITION_SUSPENSION);
    final ProcessDefinitionSuspensionRecord value = brokerRequest.getRequestWriter();
    assertThat(value.getProcessDefinitionKey()).isEqualTo(PROCESS_DEFINITION_KEY);
    assertThat(value.getVersion()).isEqualTo(-1);
    assertThat(value.isIncludeProcessInstances()).isTrue();

    assertThat(response.getProcessDefinitionsList())
        .extracting(ResumeProcessDefinitionResponse.ProcessDefinitionVersion::getOutcome)
        .containsExactly(
            ResumeProcessDefinitionResponse.ProcessDefinitionVersion.Outcome.RESUMED,
            ResumeProcessDefinitionResponse.ProcessDefinitionVersion.Outcome.ALREADY_RESUMED);
    assertThat(response.getBatchOperationKey()).isEqualTo(String.valueOf(BATCH_OPERATION_KEY));
  }

  @Test
  public void shouldMapResumptionByIdWithVersion() {
    // given
    stubResumption(suspensionRecord(-1L));
    final var request =
        ResumeProcessDefinitionRequest.newBuilder()
            .setBpmnProcessId(BPMN_PROCESS_ID)
            .setVersion(2)
            .build();

    // when
    client.resumeProcessDefinition(request);

    // then
    final BrokerResumeProcessDefinitionRequest brokerRequest =
        brokerClient.getSingleBrokerRequest();
    final ProcessDefinitionSuspensionRecord value = brokerRequest.getRequestWriter();
    assertThat(value.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(value.getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
    assertThat(value.getVersion()).isEqualTo(2);
    assertThat(value.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldRejectResumptionWithKeyAndBpmnProcessId() {
    // given
    final var request =
        ResumeProcessDefinitionRequest.newBuilder()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .setBpmnProcessId(BPMN_PROCESS_ID)
            .build();

    // when / then
    assertInvalidArgument(
        () -> client.resumeProcessDefinition(request),
        "Expected to ResumeProcessDefinition by either a process definition key or a BPMN process id, but both were provided");
  }

  @Test
  public void shouldMapInvalidStateRejectionOfResumption() {
    // given
    final var reason = "Expected to resume process definition, but it is being deleted";
    stubResumption(suspensionRecord(-1L))
        .respondWith(
            new BrokerRejectionResponse<>(
                new BrokerRejection(
                    ProcessDefinitionSuspensionIntent.RESUME,
                    1L,
                    RejectionType.INVALID_STATE,
                    reason)));
    final var request =
        ResumeProcessDefinitionRequest.newBuilder()
            .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .build();

    // when / then
    assertStatus(
        () -> client.resumeProcessDefinition(request),
        Status.Code.FAILED_PRECONDITION,
        "Command 'RESUME' rejected with code 'INVALID_STATE': " + reason);
  }

  private ProcessDefinitionSuspensionStub<BrokerSuspendProcessDefinitionRequest> stubSuspension(
      final ProcessDefinitionSuspensionRecord record) {
    final var stub =
        new ProcessDefinitionSuspensionStub<>(BrokerSuspendProcessDefinitionRequest.class, record);
    stub.registerWith(brokerClient);
    return stub;
  }

  private ProcessDefinitionSuspensionStub<BrokerResumeProcessDefinitionRequest> stubResumption(
      final ProcessDefinitionSuspensionRecord record) {
    final var stub =
        new ProcessDefinitionSuspensionStub<>(BrokerResumeProcessDefinitionRequest.class, record);
    stub.registerWith(brokerClient);
    return stub;
  }

  @Test
  public void shouldMapEveryBrokerSuspensionOutcome() {
    // given the outcomes the broker can return for a suspension
    final var outcomes =
        List.of(
            ProcessDefinitionSuspensionOutcome.SUSPENDED,
            ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED,
            ProcessDefinitionSuspensionOutcome.DRAINING);
    final var record =
        suspensionRecord(
            -1L,
            outcomes.stream()
                .map(outcome -> resolvedVersion(PROCESS_DEFINITION_KEY, 1, outcome))
                .toArray(ProcessDefinitionSuspensionResolvedVersion[]::new));

    // when
    final var response = ResponseMapper.toSuspendProcessDefinitionResponse(1L, record);

    // then
    assertThat(response.getProcessDefinitionsList())
        .extracting(version -> version.getOutcome().name())
        .containsExactlyElementsOf(outcomes.stream().map(Enum::name).toList());
  }

  @Test
  public void shouldMapEveryBrokerResumptionOutcome() {
    // given the outcomes the broker can return for a resumption
    final var outcomes =
        List.of(
            ProcessDefinitionSuspensionOutcome.RESUMED,
            ProcessDefinitionSuspensionOutcome.ALREADY_RESUMED,
            ProcessDefinitionSuspensionOutcome.DRAINING);
    final var record =
        suspensionRecord(
            -1L,
            outcomes.stream()
                .map(outcome -> resolvedVersion(PROCESS_DEFINITION_KEY, 1, outcome))
                .toArray(ProcessDefinitionSuspensionResolvedVersion[]::new));

    // when
    final var response = ResponseMapper.toResumeProcessDefinitionResponse(1L, record);

    // then
    assertThat(response.getProcessDefinitionsList())
        .extracting(version -> version.getOutcome().name())
        .containsExactlyElementsOf(outcomes.stream().map(Enum::name).toList());
  }

  private static ProcessDefinitionSuspensionRecord suspensionRecord(
      final long batchOperationKey,
      final ProcessDefinitionSuspensionResolvedVersion... resolvedVersions) {
    final var record = new ProcessDefinitionSuspensionRecord();
    record.setBatchOperationKey(batchOperationKey);
    for (final var resolvedVersion : resolvedVersions) {
      record.addResolvedVersion(resolvedVersion);
    }
    return record;
  }

  private static ProcessDefinitionSuspensionResolvedVersion resolvedVersion(
      final long processDefinitionKey,
      final int version,
      final ProcessDefinitionSuspensionOutcome outcome) {
    return new ProcessDefinitionSuspensionResolvedVersion()
        .setProcessDefinitionKey(processDefinitionKey)
        .setVersion(version)
        .setOutcome(outcome);
  }

  private static void assertInvalidArgument(final Runnable call, final String messagePart) {
    assertThatThrownBy(call::run)
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(
            error -> {
              final var status = ((StatusRuntimeException) error).getStatus();
              assertThat(status.getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
              assertThat(status.getDescription()).contains(messagePart);
            });
  }

  private static void assertStatus(
      final Runnable call, final Status.Code code, final String description) {
    assertThatThrownBy(call::run)
        .isInstanceOf(StatusRuntimeException.class)
        .satisfies(
            error -> {
              final var status = ((StatusRuntimeException) error).getStatus();
              assertThat(status.getCode()).isEqualTo(code);
              assertThat(status.getDescription()).isEqualTo(description);
            });
  }
}
