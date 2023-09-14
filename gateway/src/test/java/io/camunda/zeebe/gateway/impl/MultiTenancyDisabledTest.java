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
import static org.assertj.core.api.Assumptions.assumeThat;

import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.gateway.api.deployment.DeployResourceStub;
import io.camunda.zeebe.gateway.api.process.CreateProcessInstanceStub;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerExecuteCommand;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.grpc.Status;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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

  private void assertThatDefaultTenantIdSet() {
    final var brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(((BrokerExecuteCommand<?>) brokerRequest).getAuthorization().toDecodedMap())
        .describedAs("The broker request should contain the default tenant as authorized tenant")
        .hasEntrySatisfying(
            Authorization.AUTHORIZED_TENANTS,
            v -> assertThat(v).asList().contains(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    assumeThat(brokerRequest.getRequestWriter())
        .describedAs(
            "The rest of this assertion only makes sense when the broker request contains a record that is TenantOwned")
        .isInstanceOf(TenantOwned.class);
    assertThat(((TenantOwned) brokerRequest.getRequestWriter()).getTenantId())
        .describedAs("The tenant id should be set to the default tenant")
        .isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private void assertThatRejectsRequest(final ThrowingCallable requestCallable, final String name) {
    assertThatThrownBy(requestCallable)
        .is(statusRuntimeExceptionWithStatusCode(Status.INVALID_ARGUMENT.getCode()))
        .hasMessageContaining("Expected to handle gRPC request " + name + " with tenant identifier")
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
}
