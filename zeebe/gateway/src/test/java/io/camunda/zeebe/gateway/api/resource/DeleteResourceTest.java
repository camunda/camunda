/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.api.resource;

import static io.camunda.zeebe.gateway.api.resource.DeleteResourceStub.RESOURCE_DELETION_RECORD;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceResponse;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
import org.junit.Test;

public class DeleteResourceTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final var stub = new DeleteResourceStub();
    stub.registerWith(brokerClient);
    final var request =
        DeleteResourceRequest.newBuilder()
            .setResourceKey(RESOURCE_DELETION_RECORD.getResourceKey())
            .build();

    // when
    final DeleteResourceResponse response = client.deleteResource(request);

    // then
    assertThat(response).isNotNull();

    final BrokerDeleteResourceRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(ResourceDeletionIntent.DELETE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.RESOURCE_DELETION);

    final ResourceDeletionRecord brokeRequestValue = brokerRequest.getRequestWriter();
    assertThat(brokeRequestValue.getResourceKey())
        .isEqualTo(RESOURCE_DELETION_RECORD.getResourceKey());
  }
}
