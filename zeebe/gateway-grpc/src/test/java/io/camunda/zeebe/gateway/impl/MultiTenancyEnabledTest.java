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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
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
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessMetadata;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.grpc.Status;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

public class MultiTenancyEnabledTest extends GatewayTest {

  private final ActivateJobsStub activateJobsStub = new ActivateJobsStub();

  public MultiTenancyEnabledTest() {
    super(cfg -> cfg.getMultiTenancy().setEnabled(true));
  }

  @Before
  public void setup() {
    new DeployResourceStub().registerWith(brokerClient);
    new CreateProcessInstanceStub().registerWith(brokerClient);
    new TenantAwareEvaluateDecisionStub().registerWith(brokerClient);
    new BroadcastSignalStub().registerWith(brokerClient);
    activateJobsStub.registerWith(brokerClient);
  }

  private void assertThatAuthorizedTenantIdsSet(final List<String> authorizedTenants) {
    final var brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(((BrokerExecuteCommand<?>) brokerRequest).getAuthorization().toDecodedMap())
        .describedAs(
            "The broker request should contain '%s' as authorized tenant", authorizedTenants)
        .hasEntrySatisfying(
            Authorization.AUTHORIZED_TENANTS,
            v -> assertThat(v).asList().containsExactlyElementsOf(authorizedTenants));
  }

  private void assertThatTenantIdsSet(final String owningTenantId) {
    final var brokerRequest = brokerClient.getSingleBrokerRequest();
    assumeThat(brokerRequest.getRequestWriter())
        .describedAs(
            "The rest of this assertion only makes sense when the broker request contains a record that is TenantOwned")
        .isInstanceOf(TenantOwned.class);
    assertThat(((TenantOwned) brokerRequest.getRequestWriter()).getTenantId())
        .describedAs("The tenant id should be set to the provided tenant")
        .isEqualTo(owningTenantId);
  }

  private void assertThatRejectsRequestMissingTenantId(
      final ThrowingCallable requestCallable, final String name) {
    assertThatThrownBy(requestCallable)
        .is(statusRuntimeExceptionWithStatusCode(Status.INVALID_ARGUMENT.getCode()))
        .hasMessageContaining(
            "Expected to handle gRPC request " + name + " with tenant identifier ''")
        .hasMessageContaining("but no tenant identifier was provided");
  }

  private void assertThatRejectsUnauthorizedRequest(
      final ThrowingCallable requestCallable, final String name) {
    assertThatThrownBy(requestCallable)
        .is(statusRuntimeExceptionWithStatusCode(Status.PERMISSION_DENIED.getCode()))
        .hasMessageContaining(
            "Expected to handle gRPC request " + name + " with tenant identifier 'tenant-c'")
        .hasMessageContaining("but tenant is not authorized to perform this request");
  }

  @Test
  public void deployResourceRequestShouldContainAuthorizedTenants() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    // when
    final DeployResourceResponse response =
        client.deployResource(DeployResourceRequest.newBuilder().setTenantId("tenant-b").build());
    assertThat(response).isNotNull();

    // then
    assertThatAuthorizedTenantIdsSet(List.of("tenant-a", "tenant-b"));
    assertThatTenantIdsSet("tenant-b");
  }

  @Test
  public void deployResourceRequestRequiresTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
    final var request = DeployResourceRequest.newBuilder().build();

    // when/then
    assertThatRejectsRequestMissingTenantId(() -> client.deployResource(request), "DeployResource");
  }

  @Test
  public void deployResourceRequestRequiresValidTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
    final var request = DeployResourceRequest.newBuilder().setTenantId("tenant-c").build();

    // when/then
    assertThatRejectsUnauthorizedRequest(() -> client.deployResource(request), "DeployResource");
  }

  @Test
  public void deployResourceResponseHasTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

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
    final DeployResourceResponse response =
        client.deployResource(requestBuilder.setTenantId("tenant-b").build());
    assertThat(response).isNotNull();

    // then
    assertThat(response.getTenantId()).isEqualTo("tenant-b");

    assumeThat(response.getDeploymentsCount())
        .describedAs("Any metadata of the deployed resources should also contain the tenant id")
        .isEqualTo(3);
    final ProcessMetadata process = response.getDeployments(0).getProcess();
    assertThat(process.getTenantId()).isEqualTo("tenant-b");

    final DecisionMetadata decision = response.getDeployments(1).getDecision();
    assertThat(decision.getTenantId()).isEqualTo("tenant-b");

    final DecisionRequirementsMetadata drg = response.getDeployments(2).getDecisionRequirements();
    assertThat(drg.getTenantId()).isEqualTo("tenant-b");
  }

  @Test
  public void createProcessInstanceRequestShouldContainAuthorizedTenants() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    // when
    final CreateProcessInstanceResponse response =
        client.createProcessInstance(
            CreateProcessInstanceRequest.newBuilder().setTenantId("tenant-b").build());
    assertThat(response).isNotNull();

    // then
    assertThatAuthorizedTenantIdsSet(List.of("tenant-a", "tenant-b"));
    assertThatTenantIdsSet("tenant-b");
  }

  @Test
  public void createProcessInstanceRequestRequiresTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
    final var request = CreateProcessInstanceRequest.newBuilder().build();

    // when/then
    assertThatRejectsRequestMissingTenantId(
        () -> client.createProcessInstance(request), "CreateProcessInstance");
  }

  @Test
  public void createProcessInstanceRequestRequiresValidTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
    final var request = CreateProcessInstanceRequest.newBuilder().setTenantId("tenant-c").build();

    // when/then
    assertThatRejectsUnauthorizedRequest(
        () -> client.createProcessInstance(request), "CreateProcessInstance");
  }

  @Test
  public void createProcessInstanceResponseHasTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    // when
    final CreateProcessInstanceResponse response =
        client.createProcessInstance(
            CreateProcessInstanceRequest.newBuilder().setTenantId("tenant-b").build());
    assertThat(response).isNotNull();

    // then
    assertThat(response.getTenantId()).isEqualTo("tenant-b");
  }

  @Test
  public void evaluateDecisionRequestShouldContainAuthorizedTenants() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    final var request = EvaluateDecisionRequest.newBuilder().setTenantId("tenant-b").build();

    // when
    final EvaluateDecisionResponse response = client.evaluateDecision(request);
    assertThat(response).isNotNull();

    // then
    assertThatAuthorizedTenantIdsSet(List.of("tenant-a", "tenant-b"));
    assertThatTenantIdsSet("tenant-b");
  }

  @Test
  public void evaluateDecisionRequestRequiresTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    final var request = EvaluateDecisionRequest.newBuilder().build();

    // when/then
    assertThatRejectsRequestMissingTenantId(
        () -> client.evaluateDecision(request), "EvaluateDecision");
  }

  @Test
  public void evaluateDecisionRequestRequiresValidTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    final var request = EvaluateDecisionRequest.newBuilder().setTenantId("tenant-c").build();

    // when/then
    assertThatRejectsUnauthorizedRequest(
        () -> client.evaluateDecision(request), "EvaluateDecision");
  }

  @Test
  public void evaluateDecisionResponseHasTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    final var request = EvaluateDecisionRequest.newBuilder().setTenantId("tenant-b").build();

    // when
    final EvaluateDecisionResponse response = client.evaluateDecision(request);
    assertThat(response).isNotNull();

    // then
    assertThat(response.getTenantId()).isEqualTo("tenant-b");
  }

  @Test
  public void activateJobsRequestShouldContainAuthorizedTenantIds() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
    final String jobType = "testType";
    final String jobWorker = "testWorker";
    final int maxJobsToActivate = 1;
    activateJobsStub.addAvailableJobs(jobType, maxJobsToActivate);

    // when
    final Iterator<ActivateJobsResponse> response =
        client.activateJobs(
            ActivateJobsRequest.newBuilder()
                .setType(jobType)
                .setWorker(jobWorker)
                .setMaxJobsToActivate(maxJobsToActivate)
                .addTenantIds("tenant-b")
                .build());
    assertThat(response.hasNext()).isTrue();

    // then
    assertThatAuthorizedTenantIdsSet(List.of("tenant-a", "tenant-b"));
  }

  @Test
  public void activateJobsRequestRequiresTenantIds() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
    final String jobType = "testType";
    final String jobWorker = "testWorker";
    final int maxJobsToActivate = 1;
    activateJobsStub.addAvailableJobs(jobType, maxJobsToActivate);
    final var request =
        ActivateJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(jobWorker)
            .setMaxJobsToActivate(maxJobsToActivate)
            .build();
    final var response = client.activateJobs(request);

    // when/then
    assertThatThrownBy(() -> response.next())
        .is(statusRuntimeExceptionWithStatusCode(Status.INVALID_ARGUMENT.getCode()))
        .hasMessageContaining(
            "Expected to handle gRPC request ActivateJobs with tenant identifiers")
        .hasMessageContaining("but no tenant identifiers were provided");
  }

  @Test
  public void activateJobsRequestRequiresValidTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
    final String jobType = "testType";
    final String jobWorker = "testWorker";
    final int maxJobsToActivate = 1;
    activateJobsStub.addAvailableJobs(jobType, maxJobsToActivate);
    final var request =
        ActivateJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(jobWorker)
            .setMaxJobsToActivate(maxJobsToActivate)
            .addTenantIds("tenant-c")
            .build();

    // when
    final var response = client.activateJobs(request);

    // then
    assertThatRejectsUnauthorizedRequest(() -> response.next(), "ActivateJobs");
  }

  @Test
  public void activateJobsRequestRequiresValidTenantIds() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
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
            .addTenantIds("tenant-c")
            .build();

    // when
    final var response = client.activateJobs(request);

    // then
    assertThatRejectsUnauthorizedRequest(() -> response.next(), "ActivateJobs");
  }

  @Test
  public void activateJobsResponseHasTenantIds() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
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
                .addTenantIds("tenant-b")
                .build());
    assertThat(responses.hasNext()).isTrue();

    // then
    final ActivateJobsResponse response = responses.next();
    for (final ActivatedJob activatedJob : response.getJobsList()) {
      assertThat(activatedJob.getTenantId()).isEqualTo("tenant-b");
    }
  }

  @Test
  public void streamJobsRequestRequiresTenantIds() {
    // given
    final String jobType = "testType";
    final String jobWorker = "testWorker";
    final StreamActivatedJobsRequest request =
        StreamActivatedJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(jobWorker)
            .setTimeout(Duration.ofMinutes(1).toMillis())
            .build();
    final TestStreamObserver streamObserver = new TestStreamObserver();

    // when
    asyncClient.streamActivatedJobs(request, streamObserver);

    // then
    Awaitility.await("until validation error propagated")
        .until(() -> !streamObserver.getErrors().isEmpty());
    assertThat(streamObserver.getErrors().get(0))
        .is(statusRuntimeExceptionWithStatusCode(Status.INVALID_ARGUMENT.getCode()))
        .hasMessageContaining("Expected to handle gRPC request StreamActivatedJobs")
        .hasMessageContaining("but no tenant identifiers were provided");
  }

  @Test
  public void streamJobsRequestRequiresValidTenantIds() {
    // given
    final String jobType = "testType";
    final String jobWorker = "testWorker";
    final StreamActivatedJobsRequest request =
        StreamActivatedJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(jobWorker)
            .setTimeout(Duration.ofMinutes(1).toMillis())
            .addTenantIds("test-tenant!@#")
            .build();
    final TestStreamObserver streamObserver = new TestStreamObserver();

    // when
    asyncClient.streamActivatedJobs(request, streamObserver);

    // then
    Awaitility.await("until validation error propagated")
        .until(() -> !streamObserver.getErrors().isEmpty());
    assertThat(streamObserver.getErrors().get(0))
        .is(statusRuntimeExceptionWithStatusCode(Status.INVALID_ARGUMENT.getCode()))
        .hasMessageContaining("Expected to handle gRPC request StreamActivatedJobs")
        .hasMessageContaining("but tenant identifier contains illegal characters");
  }

  @Test
  public void streamJobsRequestRequiresAuthorizedTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
    final String jobType = "testType";
    final String jobWorker = "testWorker";
    final StreamActivatedJobsRequest request =
        StreamActivatedJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(jobWorker)
            .setTimeout(Duration.ofMinutes(1).toMillis())
            .addTenantIds("tenant-c")
            .build();
    final TestStreamObserver streamObserver = new TestStreamObserver();

    // when
    asyncClient.streamActivatedJobs(request, streamObserver);

    // then
    Awaitility.await("until validation error propagated")
        .until(() -> !streamObserver.getErrors().isEmpty());
    assertThat(streamObserver.getErrors().get(0))
        .is(statusRuntimeExceptionWithStatusCode(Status.PERMISSION_DENIED.getCode()))
        .hasMessageContaining("Expected to handle gRPC request StreamActivatedJobs")
        .hasMessageContaining("tenant is not authorized to perform this request");
  }

  @Test
  public void streamJobsRequestRequiresAuthorizedTenantIds() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
    final String jobType = "testType";
    final String jobWorker = "testWorker";
    final StreamActivatedJobsRequest request =
        StreamActivatedJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker(jobWorker)
            .setTimeout(Duration.ofMinutes(1).toMillis())
            .addTenantIds("tenant-a")
            .addTenantIds("tenant-c")
            .build();
    final TestStreamObserver streamObserver = new TestStreamObserver();

    // when
    asyncClient.streamActivatedJobs(request, streamObserver);

    // then
    Awaitility.await("until validation error propagated")
        .until(() -> !streamObserver.getErrors().isEmpty());
    assertThat(streamObserver.getErrors().get(0))
        .is(statusRuntimeExceptionWithStatusCode(Status.PERMISSION_DENIED.getCode()))
        .hasMessageContaining("Expected to handle gRPC request StreamActivatedJobs")
        .hasMessageContaining("tenant is not authorized to perform this request");
  }

  @Test
  public void broadcastSignalRequestShouldContainAuthorizedTenants() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    // when
    final BroadcastSignalResponse response =
        client.broadcastSignal(BroadcastSignalRequest.newBuilder().setTenantId("tenant-b").build());
    assertThat(response).isNotNull();

    // then
    assertThatAuthorizedTenantIdsSet(List.of("tenant-a", "tenant-b"));
    assertThatTenantIdsSet("tenant-b");
  }

  @Test
  public void broadcastSignalRequestRequiresTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
    final var request = BroadcastSignalRequest.newBuilder().build();

    // when/then
    assertThatRejectsRequestMissingTenantId(
        () -> client.broadcastSignal(request), "BroadcastSignal");
  }

  @Test
  public void broadcastSignalRequestRequiresValidTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));
    final var request = BroadcastSignalRequest.newBuilder().setTenantId("tenant-c").build();

    // when/then
    assertThatRejectsUnauthorizedRequest(() -> client.broadcastSignal(request), "BroadcastSignal");
  }

  @Test
  public void broadcastSignalResponseHasTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    // when
    final BroadcastSignalRequest request =
        BroadcastSignalRequest.newBuilder().setSignalName("test").setTenantId("tenant-b").build();
    final BroadcastSignalResponse response = client.broadcastSignal(request);
    assertThat(response).isNotNull();

    // then
    assertThat(response.getTenantId()).isEqualTo("tenant-b");
  }
}
