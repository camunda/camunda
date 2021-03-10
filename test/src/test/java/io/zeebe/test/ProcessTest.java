/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.ProcessInstanceEvent;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ProcessTest {
  @Rule public final ZeebeTestRule testRule = new ZeebeTestRule();

  private ZeebeClient client;

  @Before
  public void deploy() {
    client = testRule.getClient();

    client.newDeployCommand().addResourceFromClasspath("process.bpmn").send().join();

    RecordingExporter.deploymentRecords(DeploymentIntent.CREATED).getFirst();
  }

  @Test
  public void shouldCompleteProcessInstance() {
    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    client
        .newWorker()
        .jobType("task")
        .handler((c, j) -> c.newCompleteCommand(j.getKey()).send().join())
        .name("test")
        .open();

    ZeebeTestRule.assertThat(processInstance)
        .isEnded()
        .hasPassed("start", "task", "end")
        .hasEntered("task")
        .hasCompleted("task");
  }

  @Test
  public void shouldCompleteProcessInstanceWithVariables() {
    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    final Map<String, Object> variables = new HashMap<>();
    variables.put("a", "foo");
    variables.put("b", 123);
    variables.put("c", true);
    variables.put("d", null);

    client
        .newWorker()
        .jobType("task")
        .handler((c, j) -> c.newCompleteCommand(j.getKey()).variables(variables).send().join())
        .name("test")
        .open();

    ZeebeTestRule.assertThat(processInstance)
        .isEnded()
        .hasVariable("a", "foo")
        .hasVariable("b", 123)
        .hasVariable("c", true)
        .hasVariable("d", null);
  }
}
