/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.processing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.RegressionTest;
import io.grpc.Status.Code;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;

@ZeebeIntegration
public class MultipleInvalidResourceDeletionTest {

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          // disable long polling to increase the load in streamprocessor
          .withUnifiedConfig(cfg -> cfg.getApi().getLongPolling().setEnabled(false))
          .withRecordingExporter(true);

  private final PartitionsActuator partitions = PartitionsActuator.of(zeebe);
  @AutoClose private CamundaClient client;

  @BeforeEach
  void beforeEach() {
    client = zeebe.newClientBuilder().preferRestOverGrpc(false).build();
  }

  @RegressionTest("https://github.com/camunda/camunda/issues/16429")
  public void shouldRejectMultipleResourceDeletion() {
    // given
    // generate load on stream processor. This is to increase the chance of the issue to happen.
    client
        .newWorker()
        .jobType("test")
        .handler(
            (jobClient, job) -> {
              jobClient.newCompleteCommand(job.getKey()).send().join();
            })
        .open();

    // when
    final var firstRejection =
        client.newDeleteResourceCommand(Protocol.encodePartitionId(1, 999999L)).send();
    final var secondRejection =
        client.newDeleteResourceCommand(Protocol.encodePartitionId(1, 999999L)).send();
    final var thirdRejection =
        client.newDeleteResourceCommand(Protocol.encodePartitionId(1, 999999L)).send();

    // then
    // All requests should be rejected. In the original issue, the second/third requests can timeout
    assertThatThrownBy(firstRejection::join)
        .isInstanceOf(ClientStatusException.class)
        .extracting("status.code")
        .isEqualTo(Code.NOT_FOUND);

    assertThatThrownBy(secondRejection::join)
        .isInstanceOf(ClientStatusException.class)
        .extracting("status.code")
        .isEqualTo(Code.NOT_FOUND);

    assertThatThrownBy(thirdRejection::join)
        .isInstanceOf(ClientStatusException.class)
        .extracting("status.code")
        .isEqualTo(Code.NOT_FOUND);
  }
}
