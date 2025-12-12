/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl;

import static io.camunda.zeebe.gateway.api.util.GatewayAssertions.statusRuntimeExceptionWithStatusCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.google.protobuf.ByteString;
import io.camunda.zeebe.gateway.api.conditional.EvaluateConditionalStub;
import io.camunda.zeebe.gateway.api.decision.EvaluateDecisionStub;
import io.camunda.zeebe.gateway.api.deployment.DeployResourceStub;
import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.job.TestStreamObserver;
import io.camunda.zeebe.gateway.api.process.CreateProcessInstanceStub;
import io.camunda.zeebe.gateway.api.signal.BroadcastSignalStub;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DecisionMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DecisionRequirementsMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest.Builder;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateConditionalRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.grpc.Status;
import java.time.Duration;
import java.util.Iterator;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

public class MultiTenancyDisabledTest extends GatewayTest {

  private final ActivateJobsStub activateJobsStub = new ActivateJobsStub();

  public MultiTenancyDisabledTest() {
    super(cfg -> {}, cfg -> cfg.getMultiTenancy().setChecksEnabled(false));
  }

  @Before
  public void setup() {
    new DeployResourceStub().registerWith(brokerClient);
    new CreateProcessInstanceStub().registerWith(brokerClient);
    new EvaluateDecisionStub().registerWith(brokerClient);
    new BroadcastSignalStub().registerWith(brokerClient);
    new EvaluateConditionalStub().registerWith(brokerClient);
    activateJobsStub.registerWith(brokerClient);
  }

  private void assertThatDefaultTenantIdSet() {
    final var brokerRequest = brokerClient.getSingleBrokerRequest();
    assumeThat(brokerRequest.getRequestWriter())
        .describedAs(
            "The rest of this assertion only makes sense when the broker request contains a record that is TenantOwned")
        .isInstanceOf(TenantOwned.class);
    assertThat(((TenantOwned) brokerRequest.getRequestWriter()).getTenantId())
        .describedAs("The tenant id should be set to the <default> tenant")
        .isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private void assertThatRejectsRequest(final ThrowingCallable requestCallable, final String name) {
    assertThatThrownBy(requestCallable)
        .is(statusRuntimeExceptionWithStatusCode(Status.INVALID_ARGUMENT.getCode()))
        .hasMessageContaining("but multi-tenancy is disabled");
  }

  @Test
  public void deployResourceRequestShouldContainDefaultTenantAsAuthorizedTenants() {
    // given
    final var request = DeployResourceRequest.newBuilder().build();

    // when
    final var response = client.deployResource(request);
    assertThat(response).isNotNull();

    // then
    assertThatDefaultTenantIdSet();
  }

  @Test
  public void deployResourceRequestRejectsTenantId() {
    // given
    final var request = DeployResourceRequest.newBuilder().setTenantId("tenant-a").build();

    // when/then
    assertThatRejectsRequest(() -> client.deployResource(request), "DeployResource");
  }

  @Test
  public void deployResourceResponseHasTenantId() {
    // when
    final Builder requestBuilder = DeployResourceRequest.newBuilder();
    requestBuilder
        .addResourcesBuilder()
        .setName("testProcess.bpmn")
        .setContent(ByteString.copyFromUtf8("<xml/>"));
    requestBuilder
        .addResourcesBuilder()
        .setName("testDecision.dmn")
        .setContent(ByteString.copyFromUtf8("test"));
    final DeployResourceResponse response = client.deployResource(requestBuilder.build());
    assertThat(response).isNotNull();

    // then
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assumeThat(response.getDeploymentsCount())
        .describedAs("Any metadata of the deployed resources should also contain the tenant id")
        .isEqualTo(3);
    final ProcessMetadata process = response.getDeployments(0).getProcess();
    assertThat(process.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    final DecisionMetadata decision = response.getDeployments(1).getDecision();
    assertThat(decision.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    final DecisionRequirementsMetadata drg = response.getDeployments(2).getDecisionRequirements();
    assertThat(drg.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void createProcessInstanceRequestShouldContainDefaultTenantAsAuthorizedTenants() {
    // given
    final var request = CreateProcessInstanceRequest.newBuilder().build();

    // when
    final var response = client.createProcessInstance(request);
    assertThat(response).isNotNull();

    // then
    assertThatDefaultTenantIdSet();
  }

  @Test
  public void createProcessInstanceRequestRejectsTenantId() {
    // given
    final var request = CreateProcessInstanceRequest.newBuilder().setTenantId("tenant-a").build();

    // when/then
    assertThatRejectsRequest(() -> client.createProcessInstance(request), "CreateProcessInstance");
  }

  @Test
  public void createProcessInstanceResponseHasTenantId() {
    // when
    final CreateProcessInstanceResponse response =
        client.createProcessInstance(CreateProcessInstanceRequest.newBuilder().build());
    assertThat(response).isNotNull();

    // then
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void evaluateDecisionShouldContainDefaultTenantAsAuthorizedTenants() {
    // given
    final var request = EvaluateDecisionRequest.newBuilder().build();

    // when
    final var response = client.evaluateDecision(request);
    assertThat(response).isNotNull();

    // then
    assertThatDefaultTenantIdSet();
  }

  @Test
  public void evaluateDecisionRequestRejectsTenantId() {
    // given
    final var request = EvaluateDecisionRequest.newBuilder().setTenantId("tenant-a").build();

    // when/then
    assertThatRejectsRequest(() -> client.evaluateDecision(request), "EvaluateDecision");
  }

  @Test
  public void evaluateDecisionResponseHasTenantId() {
    final var request = EvaluateDecisionRequest.newBuilder().build();

    // when
    final EvaluateDecisionResponse response = client.evaluateDecision(request);
    assertThat(response).isNotNull();

    // then
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void activateJobsRequestRejectsTenantId() {
    // given
    final String jobType = "testType";
    final String jobWorker = "testWorker";
    final int maxJobsToActivate = 1;
    activateJobsStub.addAvailableJobs(jobType, maxJobsToActivate);
    final var request =
        ActivateJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(jobWorker)
            .setMaxJobsToActivate(maxJobsToActivate)
            .addTenantIds("tenant-a")
            .build();

    // when
    final var response = client.activateJobs(request);

    // then
    assertThatRejectsRequest(() -> response.hasNext(), "ActivateJobs");
  }

  @Test
  public void activateJobsResponseHasTenantId() {
    // given
    final String jobType = "testType";
    final String jobWorker = "testWorker";
    final int maxJobsToActivate = 1;
    activateJobsStub.addAvailableJobs(jobType, maxJobsToActivate);

    // when
    final Iterator<ActivateJobsResponse> responses =
        client.activateJobs(
            ActivateJobsRequest.newBuilder()
                .setType(jobType)
                .setWorker(jobWorker)
                .setMaxJobsToActivate(maxJobsToActivate)
                .build());
    assertThat(responses.hasNext()).isTrue();

    // then
    final ActivateJobsResponse response = responses.next();
    for (final ActivatedJob activatedJob : response.getJobsList()) {
      assertThat(activatedJob.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }
  }

  @Test
  public void streamJobsRequestRejectsTenantIds() {
    // given
    final String jobType = "testType";
    final String jobWorker = "testWorker";
    final StreamActivatedJobsRequest request =
        StreamActivatedJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(jobWorker)
            .setTimeout(Duration.ofMinutes(1).toMillis())
            .addTenantIds("tenant-a")
            .build();
    final TestStreamObserver streamObserver = new TestStreamObserver();

    // when
    asyncClient.streamActivatedJobs(request, streamObserver);

    // then
    Awaitility.await("until validation error propagated")
        .until(() -> !streamObserver.getErrors().isEmpty());
    assertThat(streamObserver.getErrors().get(0))
        .is(statusRuntimeExceptionWithStatusCode(Status.INVALID_ARGUMENT.getCode()))
        .hasMessageContaining("Expected to handle request StreamActivatedJobs")
        .hasMessageContaining("but multi-tenancy is disabled");
  }

  @Test
  public void broadcastSignalRequestShouldContainDefaultTenantAsAuthorizedTenants() {
    // given
    final var request = BroadcastSignalRequest.newBuilder().build();

    // when
    final var response = client.broadcastSignal(request);
    assertThat(response).isNotNull();

    // then
    assertThatDefaultTenantIdSet();
  }

  @Test
  public void broadcastSignalRequestRejectsTenantId() {
    // given
    final var request = BroadcastSignalRequest.newBuilder().setTenantId("tenant-a").build();

    // when/then
    assertThatRejectsRequest(() -> client.broadcastSignal(request), "BroadcastSignal");
  }

  @Test
  public void broadcastSignalResponseHasTenantId() {
    // when
    final BroadcastSignalResponse response =
        client.broadcastSignal(BroadcastSignalRequest.newBuilder().build());
    assertThat(response).isNotNull();

    // then
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void evaluateConditionalShouldUseDefaultTenant() {
    // when
    client.evaluateConditional(
        EvaluateConditionalRequest.newBuilder().setVariables("{\"x\": 1}").build());

    // then
    assertThatDefaultTenantIdSet();
  }

  @Test
  public void evaluateConditionalRequestRejectsNonDefaultTenantIdWhenMultiTenancyDisabled() {
    // given
    final var request =
        EvaluateConditionalRequest.newBuilder()
            .setTenantId("tenant-a")
            .setVariables("{\"x\": 1}")
            .build();

    // when/then
    assertThatRejectsRequest(() -> client.evaluateConditional(request), "EvaluateConditional");
  }
}
