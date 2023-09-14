/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl;

import static io.camunda.zeebe.gateway.api.util.GatewayAssertions.statusRuntimeExceptionWithStatusCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.gateway.api.deployment.DeployResourceStub;
import io.camunda.zeebe.gateway.api.process.CreateProcessInstanceStub;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.grpc.Status;
import org.junit.Before;
import org.junit.Test;

public class MultiTenancyDisabledTest extends GatewayTest {

  public MultiTenancyDisabledTest() {
    super(cfg -> cfg.getMultiTenancy().setEnabled(false));
  }

  @Before
  public void setup() {
    new DeployResourceStub().registerWith(brokerClient);
    new CreateProcessInstanceStub().registerWith(brokerClient);
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
    assertThat(brokerRequest.getRequestWriter().getTenantId())
        .isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(brokerRequest.getAuthorization().toDecodedMap())
        .hasEntrySatisfying(
            Authorization.AUTHORIZED_TENANTS,
            v -> assertThat(v).asList().contains(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }

  @Test
  public void deployResourceRequestRejectsTenantId() {
    // given
    final var request = DeployResourceRequest.newBuilder().setTenantId("tenant-a").build();

    // when/then
    assertThatThrownBy(() -> client.deployResource(request))
        .is(statusRuntimeExceptionWithStatusCode(Status.INVALID_ARGUMENT.getCode()))
        .hasMessageContaining(
            "Expected to handle gRPC request DeployResource with tenant identifier")
        .hasMessageContaining("but multi-tenancy is disabled");
  }

  @Test
  public void createProcessInstanceRequestShouldContainDefaultTenantAsAuthorizedTenants() {
    // given
    final var request = CreateProcessInstanceRequest.newBuilder().build();

    // when
    final var response = client.createProcessInstance(request);
    assertThat(response).isNotNull();

    // then
    final BrokerCreateProcessInstanceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getRequestWriter().getTenantId())
        .isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(brokerRequest.getAuthorization().toDecodedMap())
        .hasEntrySatisfying(
            Authorization.AUTHORIZED_TENANTS,
            v -> assertThat(v).asList().contains(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  }
}
