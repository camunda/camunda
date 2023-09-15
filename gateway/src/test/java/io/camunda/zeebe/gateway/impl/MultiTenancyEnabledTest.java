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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.tenants.dto.Tenant;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.gateway.api.deployment.DeployResourceStub;
import io.camunda.zeebe.gateway.api.process.CreateProcessInstanceStub;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerExecuteCommand;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeployResourceResponse;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.grpc.Status;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class MultiTenancyEnabledTest extends GatewayTest {

  public MultiTenancyEnabledTest() {
    super(cfg -> cfg.getMultiTenancy().setEnabled(true));
  }

  @Before
  public void setup() {
    new DeployResourceStub().registerWith(brokerClient);
    new CreateProcessInstanceStub().registerWith(brokerClient);
  }

  private void assertThatTenantIdsSet(
      final String owningTenantId, final List<String> authorizedTenants) {
    final var brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(((BrokerExecuteCommand<?>) brokerRequest).getAuthorization().toDecodedMap())
        .describedAs(
            "The broker request should contain '%s' as authorized tenant", authorizedTenants)
        .hasEntrySatisfying(
            Authorization.AUTHORIZED_TENANTS,
            v -> assertThat(v).asList().containsExactlyElementsOf(authorizedTenants));

    assumeThat(brokerRequest.getRequestWriter())
        .describedAs(
            "The rest of this assertion only makes sense when the broker request contains a record that is TenantOwned")
        .isInstanceOf(TenantOwned.class);
    assertThat(((TenantOwned) brokerRequest.getRequestWriter()).getTenantId())
        .describedAs("The tenant id should be set to the provided tenant")
        .isEqualTo(owningTenantId);
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
    assertThatTenantIdsSet("tenant-b", List.of("tenant-a", "tenant-b"));
  }

  @Test
  public void deployResourceRequestRequiresTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    // when/then
    assertThatThrownBy(() -> client.deployResource(DeployResourceRequest.newBuilder().build()))
        .is(statusRuntimeExceptionWithStatusCode(Status.INVALID_ARGUMENT.getCode()))
        .hasMessageContaining(
            "Expected to handle gRPC request DeployResource with tenant identifier ``")
        .hasMessageContaining("but no tenant identifier was provided");
  }

  @Test
  public void deployResourceRequestRequiresValidTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    // when/then
    assertThatThrownBy(
            () ->
                client.deployResource(
                    DeployResourceRequest.newBuilder().setTenantId("tenant-c").build()))
        .is(statusRuntimeExceptionWithStatusCode(Status.PERMISSION_DENIED.getCode()))
        .hasMessageContaining(
            "Expected to handle gRPC request DeployResource with tenant identifier `tenant-c`")
        .hasMessageContaining("but tenant is not authorized to perform this request");
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
    assertThatTenantIdsSet("tenant-b", List.of("tenant-a", "tenant-b"));
  }

  @Test
  public void createProcessInstanceRequestRequiresTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    // when/then
    assertThatThrownBy(
            () -> client.createProcessInstance(CreateProcessInstanceRequest.newBuilder().build()))
        .is(statusRuntimeExceptionWithStatusCode(Status.INVALID_ARGUMENT.getCode()))
        .hasMessageContaining(
            "Expected to handle gRPC request CreateProcessInstance with tenant identifier ``")
        .hasMessageContaining("but no tenant identifier was provided");
  }

  @Test
  public void createProcessInstanceRequestRequiresValidTenantId() {
    // given
    when(gateway.getIdentityMock().tenants().forToken(anyString()))
        .thenReturn(List.of(new Tenant("tenant-a", "A"), new Tenant("tenant-b", "B")));

    // when/then
    assertThatThrownBy(
            () ->
                client.createProcessInstance(
                    CreateProcessInstanceRequest.newBuilder().setTenantId("tenant-c").build()))
        .is(statusRuntimeExceptionWithStatusCode(Status.PERMISSION_DENIED.getCode()))
        .hasMessageContaining(
            "Expected to handle gRPC request CreateProcessInstance with tenant identifier `tenant-c`")
        .hasMessageContaining("but tenant is not authorized to perform this request");
  }
}
