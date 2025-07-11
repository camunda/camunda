/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
public class LargeVariableIT {

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void shouldHandleTruncatedVariables() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .name("start")
            .endEvent()
            .name("end")
            .done();

    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    final Map<String, Object> variables = new HashMap<>();
    variables.put("small", "smallValue");
    variables.put("large", createLargeString(100));

    final ProcessInstanceEvent processInstance =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    CamundaAssert.assertThat(processInstance).isCompleted().hasVariables(variables);
  }

  private String createLargeString(final long sizeKb) {
    final StringBuilder sb = new StringBuilder();
    for (long i = 0; i < sizeKb * 1024; i++) {
      sb.append('a');
    }

    return sb.toString();
  }
}
