/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.jobstream;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class JobStreamLifecycleIT {
  @RegisterExtension
  public final ClusteringRuleExtension cluster = new ClusteringRuleExtension(3, 3, 3, cfg -> {});

  @Test
  void shouldOpenStream() throws InterruptedException {
    // given
    final var client = cluster.getClient();
    final var result =
        client
            .newStreamJobsCommand()
            .jobType("type")
            .handler(
                (c, j) -> {
                  Loggers.JOB_STREAM.debug("Completing job {}", j.getKey());
                  c.newCompleteCommand(j.getKey()).send().join();
                })
            .send();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", b -> b.zeebeJobType("type"))
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join();

    // when
    while (true) {
      client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();
      Thread.sleep(1_000);
    }

    // then
  }
}
