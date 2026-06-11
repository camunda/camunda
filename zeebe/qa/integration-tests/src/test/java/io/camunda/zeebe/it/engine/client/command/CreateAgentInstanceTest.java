/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public final class CreateAgentInstanceTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  @AutoClose CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  public void init() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Test
  public void shouldReturnUnavailableWhenElementInstanceKeyEncodesNonExistentPartition() {
    // given
    // Keys encode the target partition in their upper 13 bits; a key for a non-existent partition
    // yields 503 UNAVAILABLE (PartitionNotFoundException).
    final long elementInstanceKey = Protocol.encodePartitionId(999, 1);

    // when
    final var responseFuture =
        client
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(elementInstanceKey)
            .model("test-model")
            .provider("test-provider")
            .systemPrompt("You are a helpful assistant.")
            .send();

    // then
    assertThatThrownBy(responseFuture::join)
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("title: UNAVAILABLE")
        .hasMessageContaining("status: 503")
        .hasMessageContaining("Expected to handle request, but request could not be delivered");
  }

  @Test
  public void shouldReturnNotFoundWhenElementInstanceDoesNotExistOnExistingPartition() {
    // given
    final int partition = resourcesHelper.getPartitions().getFirst();
    final long nonExistingKey = Protocol.encodePartitionId(partition, 2);

    // when
    final var responseFuture =
        client
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(nonExistingKey)
            .model("test-model")
            .provider("test-provider")
            .systemPrompt("You are a helpful assistant.")
            .send();

    // then
    assertThatThrownBy(responseFuture::join)
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("title: NOT_FOUND")
        .hasMessageContaining("status: 404")
        .hasMessageContaining(
            "Expected to create agent instance for element instance with key '%d', but no such element instance was found."
                .formatted(nonExistingKey));
  }
}
