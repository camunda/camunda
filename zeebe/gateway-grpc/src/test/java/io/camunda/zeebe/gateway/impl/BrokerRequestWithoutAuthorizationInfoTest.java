/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.gateway.api.deployment.DeployResourceStub;
import io.camunda.zeebe.gateway.api.job.ActivateJobsStub;
import io.camunda.zeebe.gateway.api.process.CreateProcessInstanceStub;
import io.camunda.zeebe.gateway.api.signal.BroadcastSignalStub;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.BroadcastSignalResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that when both authorization and multi-tenancy checks are disabled, the gateway only
 * embeds identity claims (username, client ID) but skips authorization claims (token claims, group
 * memberships) to save MsgPack serialization cost.
 */
public class BrokerRequestWithoutAuthorizationInfoTest extends GatewayTest {

  private final ActivateJobsStub activateJobsStub = new ActivateJobsStub();

  public BrokerRequestWithoutAuthorizationInfoTest() {
    super(
        config -> {},
        security -> {
          security.getAuthorizations().setEnabled(false);
          security.getMultiTenancy().setChecksEnabled(false);
        });
  }

  @Before
  public void setup() {
    new DeployResourceStub().registerWith(brokerClient);
    new CreateProcessInstanceStub().registerWith(brokerClient);
    new BroadcastSignalStub().registerWith(brokerClient);
    activateJobsStub.registerWith(brokerClient);
  }

  @Test
  public void deployResourceRequestShouldNotContainClaims() {
    // when
    final DeployResourceResponse response =
        client.deployResource(DeployResourceRequest.newBuilder().build());
    assertThat(response).isNotNull();

    // then
    assertNoAuthorizationClaims();
  }

  @Test
  public void createProcessInstanceRequestShouldNotContainClaims() {
    // when
    final CreateProcessInstanceResponse response =
        client.createProcessInstance(CreateProcessInstanceRequest.newBuilder().build());
    assertThat(response).isNotNull();

    // then
    assertNoAuthorizationClaims();
  }

  @Test
  public void activateJobsRequestShouldNotContainClaims() {
    // given
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
                .build());
    assertThat(response.hasNext()).isTrue();

    // then
    assertNoAuthorizationClaims();
  }

  @Test
  public void broadcastSignalRequestShouldNotContainClaims() {
    // when
    final BroadcastSignalResponse response =
        client.broadcastSignal(BroadcastSignalRequest.newBuilder().build());
    assertThat(response).isNotNull();

    // then
    assertNoAuthorizationClaims();
  }

  @Test
  public void streamActivatedJobsRequestShouldNotContainClaims() {
    // given
    final String jobType = "testStreamType";
    final var request =
        StreamActivatedJobsRequest.newBuilder()
            .setType(jobType)
            .setWorker("testWorker")
            .setTimeout(Duration.ofMinutes(1).toMillis())
            .addAllFetchVariable(List.of("foo"))
            .build();

    // when
    asyncClient.streamActivatedJobs(
        request, new io.camunda.zeebe.gateway.api.job.TestStreamObserver());
    jobStreamer.waitStreamToBeAvailable(BufferUtil.wrapString(jobType));

    // then
    final var metadata = jobStreamer.getStreamMetadata(jobType);
    assertThat(metadata).isNotNull();
    assertThat(metadata.claims()).doesNotContainKey(Authorization.USER_TOKEN_CLAIMS);
    assertThat(metadata.claims()).doesNotContainKey(Authorization.USER_GROUPS_CLAIMS);
  }

  private void assertNoAuthorizationClaims() {
    final BrokerExecuteCommand<?> brokerRequest = brokerClient.getSingleBrokerRequest();
    final var claims = brokerRequest.getAuthorization().toDecodedMap();
    assertThat(claims).doesNotContainKey(Authorization.USER_TOKEN_CLAIMS);
    assertThat(claims).doesNotContainKey(Authorization.USER_GROUPS_CLAIMS);
  }
}
