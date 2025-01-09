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
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.EvaluateDecisionResponse;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

public class BrokerRequestWithAuthorizationInfoTest extends GatewayTest {

  private final ActivateJobsStub activateJobsStub = new ActivateJobsStub();

  @Before
  public void setup() {
    new DeployResourceStub().registerWith(brokerClient);
    new CreateProcessInstanceStub().registerWith(brokerClient);
    new TenantAwareEvaluateDecisionStub().registerWith(brokerClient);
    new BroadcastSignalStub().registerWith(brokerClient);
    activateJobsStub.registerWith(brokerClient);
  }

  @Test
  public void deployResourceRequestShouldContainUserClaims() {
    // when
    final DeployResourceResponse response =
        client.deployResource(DeployResourceRequest.newBuilder().build());
    assertThat(response).isNotNull();

    // then
    assertContainsUserClaims();
  }

  @Test
  public void createProcessInstanceRequestShouldContainUserClaims() {
    // when
    final CreateProcessInstanceResponse response =
        client.createProcessInstance(CreateProcessInstanceRequest.newBuilder().build());
    assertThat(response).isNotNull();

    // then
    assertContainsUserClaims();
  }

  @Test
  public void evaluateDecisionRequestShouldContainUserClaims() {
    // when
    final EvaluateDecisionResponse response =
        client.evaluateDecision(EvaluateDecisionRequest.newBuilder().build());
    assertThat(response).isNotNull();

    // then
    assertContainsUserClaims();
  }

  @Test
  public void activateJobsRequestShouldContainUserClaims() {
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
    assertContainsUserClaims();
  }

  @Test
  public void broadcastSignalRequestShouldContainUserClaims() {
    // when
    final BroadcastSignalResponse response =
        client.broadcastSignal(BroadcastSignalRequest.newBuilder().build());
    assertThat(response).isNotNull();

    // then
    assertContainsUserClaims();
  }

  private void assertContainsUserClaims() {
    assertThatAuthorizationInfoIsSet(Map.of("role", "admin", "foo", "bar", "baz", "qux"));
  }

  private void assertThatAuthorizationInfoIsSet(final Map<String, Object> claims) {
    final Map<String, Object> mapToCheck =
        claims.entrySet().stream()
            .map(
                entry ->
                    Map.entry(
                        Authorization.USER_TOKEN_CLAIM_PREFIX + entry.getKey(), entry.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    final BrokerExecuteCommand<?> brokerRequest = brokerClient.getSingleBrokerRequest();
    final Map<String, Object> decodedMap = brokerRequest.getAuthorization().getClaims();

    assertThat(decodedMap).containsAllEntriesOf(mapToCheck);
  }
}
