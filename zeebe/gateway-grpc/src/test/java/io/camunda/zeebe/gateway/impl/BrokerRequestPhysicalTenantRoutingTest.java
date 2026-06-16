/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.gateway.api.job.CompleteJobStub;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayBlockingStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CompleteJobRequest;
import io.camunda.zeebe.protocol.Protocol;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/**
 * End-to-end test of physical tenant routing through the real gRPC interceptor chain: a client sets
 * the {@code Camunda-Physical-Tenant} header, the {@code PhysicalTenantInterceptor} resolves and
 * validates it into the gRPC context, and the {@link io.camunda.zeebe.gateway.EndpointManager}
 * forwards it onto the {@link BrokerRequest} as the partition group it is dispatched to.
 */
public class BrokerRequestPhysicalTenantRoutingTest extends GatewayTest {

  private static final String KNOWN_TENANT = "tenant-a";
  private static final Metadata.Key<String> PHYSICAL_TENANT_HEADER =
      Metadata.Key.of("Camunda-Physical-Tenant", Metadata.ASCII_STRING_MARSHALLER);

  public BrokerRequestPhysicalTenantRoutingTest() {
    super(
        new GatewayCfg(),
        EngineSecurityConfigurations.defaultConfig(),
        () -> Set.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, KNOWN_TENANT));
  }

  @Before
  public void setup() {
    new CompleteJobStub().registerWith(brokerClient);
  }

  @Test
  public void shouldRouteRequestToPhysicalTenantPartitionGroup() {
    // given
    final GatewayBlockingStub tenantClient = withPhysicalTenant(KNOWN_TENANT);

    // when
    tenantClient.completeJob(CompleteJobRequest.newBuilder().setJobKey(1L).build());

    // then
    final BrokerRequest<?> brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getPartitionGroup()).isEqualTo(KNOWN_TENANT);
  }

  @Test
  public void shouldRouteToDefaultPartitionGroupWhenHeaderAbsent() {
    // when no Camunda-Physical-Tenant header is sent
    client.completeJob(CompleteJobRequest.newBuilder().setJobKey(1L).build());

    // then
    final BrokerRequest<?> brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getPartitionGroup()).isEqualTo(Protocol.DEFAULT_PARTITION_GROUP_NAME);
  }

  @Test
  public void shouldRejectRequestForUnknownPhysicalTenant() {
    // given
    final GatewayBlockingStub unknownClient = withPhysicalTenant("unknown-tenant");

    // when / then
    assertThatThrownBy(
            () -> unknownClient.completeJob(CompleteJobRequest.newBuilder().setJobKey(1L).build()))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("NOT_FOUND")
        .hasMessageContaining("unknown-tenant");
    assertThat(brokerClient.getBrokerRequests()).isEmpty();
  }

  private GatewayBlockingStub withPhysicalTenant(final String physicalTenantId) {
    final Metadata headers = new Metadata();
    headers.put(PHYSICAL_TENANT_HEADER, physicalTenantId);
    return client.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
  }
}
