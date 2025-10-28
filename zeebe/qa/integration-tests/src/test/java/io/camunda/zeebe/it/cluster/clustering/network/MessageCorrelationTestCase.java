/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class MessageCorrelationTestCase implements AsymmetricNetworkPartitionTestCase {

  @Override
  public void given(final CamundaClient client) {
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .message(msg -> msg.name("msg").zeebeCorrelationKeyExpression("key"))
            .endEvent()
            .done();
    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();
    // make sure that the message is correlated to correct partition
    assertThat(SubscriptionUtil.getSubscriptionPartitionId(BufferUtil.wrapString("key"), 2))
        .describedAs("message should correlated to partition two")
        .isEqualTo(2);

    client
        .newPublishMessageCommand()
        .messageName("msg")
        .correlationKey("key")
        .timeToLive(Duration.ofMinutes(30))
        .send()
        .join();
  }

  @Override
  public CompletableFuture<?> when(final CamundaClient client) {
    return (CompletableFuture<?>)
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .variables(Map.of("key", "key"))
            .withResult()
            .send();
  }

  @Override
  public void then(final CamundaClient client, final CompletableFuture<?> whenFuture) {
    whenFuture.join(); // await the process instance completion
  }
}
