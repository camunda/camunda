/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.gateway.impl.broker.response.BrokerResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.Before;
import org.junit.Test;

public class MultiTenancyDisabledTest extends GatewayTest {

  public MultiTenancyDisabledTest() {
    super(cfg -> cfg.getMultiTenancy().setEnabled(false));
  }

  @Before
  public void setup() {
    final FakeRequestStub stub = new FakeRequestStub();
    stub.registerWith(brokerClient);
  }

  @Test
  public void deployResourceRequestShouldContainDefaultTenantAsAuthorizedTenants() {
    // given
    final var request = DeployResourceRequest.newBuilder().build();

    // when
    final var response = client.deployResource(request);
    assertThat(response).isNotNull();

    // then
    final BrokerDeployResourceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getAuthorization().toDecodedMap())
        .hasEntrySatisfying(
            Authorization.AUTHORIZED_TENANTS,
            v -> assertThat(v).asList().contains(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  public static final class FakeRequestStub
      implements RequestStub<BrokerDeployResourceRequest, BrokerResponse<DeploymentRecord>> {

    @Override
    public void registerWith(final StubbedBrokerClient gateway) {
      gateway.registerHandler(BrokerDeployResourceRequest.class, this);
    }

    @Override
    public BrokerResponse<DeploymentRecord> handle(final BrokerDeployResourceRequest request)
        throws Exception {
      final DeploymentRecord deploymentRecord = request.getRequestWriter();
      return new BrokerResponse<>(deploymentRecord, 0, 123);
    }
  }
}
