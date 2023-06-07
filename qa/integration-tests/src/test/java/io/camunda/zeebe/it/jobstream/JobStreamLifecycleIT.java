/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.jobstream;

import io.camunda.zeebe.it.clustering.ClusteringRuleExtension;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

final class JobStreamLifecycleIT {
  @RegisterExtension
  public final ClusteringRuleExtension cluster = new ClusteringRuleExtension(1, 1, 1, cfg -> {});

  @Test
  void shouldOpenStream() throws InterruptedException {
    // given
    final var client = cluster.getClient();
    final var result = client.newStreamJobsCommand().jobType("type").handler((c, j) -> {}).send();
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
    client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    // when
    Thread.sleep(10_000);

    // then
  }
}
