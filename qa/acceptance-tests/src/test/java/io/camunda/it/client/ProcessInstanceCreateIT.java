/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.waitForProcessInstance;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.collection.Maps;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class ProcessInstanceCreateIT {

  private static CamundaClient camundaClient;
  private static final Set<String> TAGS = Set.of("tag1", "tag2");

  private static Process process;

  @BeforeAll
  public static void beforeAll() {
    final var processDefinition =
        Bpmn.createExecutableProcess("process").name("my process").startEvent().endEvent().done();

    process = deployProcessAndWaitForIt(camundaClient, processDefinition, "process.bpmn");
  }

  @Test
  void shouldCreateProcessInstance() {
    // given
    final var processInstanceCreation =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .tags(TAGS)
            .send()
            .join();

    assertThat(processInstanceCreation).isNotNull();
    assertThat(processInstanceCreation.getProcessInstanceKey()).isNotNull();
    assertThat(processInstanceCreation.getBpmnProcessId()).isEqualTo(process.getBpmnProcessId());
    assertThat(processInstanceCreation.getProcessDefinitionKey())
        .isEqualTo(process.getProcessDefinitionKey());
    assertThat(processInstanceCreation.getVersion()).isEqualTo(process.getVersion());
    assertThat(processInstanceCreation.getTenantId()).isEqualTo(process.getTenantId());
    assertThat(processInstanceCreation.getTags()).isEqualTo(TAGS);

    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(processInstanceCreation.getProcessInstanceKey()),
        f -> assertThat(f).hasSize(1));

    // when
    final var result =
        camundaClient
            .newProcessInstanceGetRequest(processInstanceCreation.getProcessInstanceKey())
            .send()
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceKey())
        .isEqualTo(processInstanceCreation.getProcessInstanceKey());
    assertThat(result.getProcessDefinitionKey()).isEqualTo(process.getProcessDefinitionKey());
    assertThat(result.getProcessDefinitionId()).isEqualTo(process.getBpmnProcessId());
    assertThat(result.getProcessDefinitionName()).isEqualTo("my process");
    assertThat(result.getProcessDefinitionVersion()).isEqualTo(1);
    assertThat(result.getTenantId()).isEqualTo("<default>");
    assertThat(result.getTags()).isEqualTo(TAGS);
  }

  @Test
  void shouldCreateProcessInstanceWithResult() {
    final Map<String, Object> variables = Maps.of(entry("foo", "bar"));
    // given
    final var processInstanceCreation =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .variables(variables)
            .tags(TAGS)
            .withResult()
            .fetchVariables("foo")
            .send()
            .join();

    assertThat(processInstanceCreation).isNotNull();
    assertThat(processInstanceCreation.getProcessInstanceKey()).isNotNull();
    assertThat(processInstanceCreation.getBpmnProcessId()).isEqualTo(process.getBpmnProcessId());
    assertThat(processInstanceCreation.getProcessDefinitionKey())
        .isEqualTo(process.getProcessDefinitionKey());
    assertThat(processInstanceCreation.getVersion()).isEqualTo(process.getVersion());
    assertThat(processInstanceCreation.getTenantId()).isEqualTo(process.getTenantId());
    assertThat(processInstanceCreation.getVariablesAsMap()).isEqualTo(variables);
    assertThat(processInstanceCreation.getTags()).isEqualTo(TAGS);

    waitForProcessInstance(
        camundaClient,
        f -> f.processInstanceKey(processInstanceCreation.getProcessInstanceKey()),
        f -> assertThat(f).hasSize(1));

    // when
    final var result =
        camundaClient
            .newProcessInstanceGetRequest(processInstanceCreation.getProcessInstanceKey())
            .send()
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceKey())
        .isEqualTo(processInstanceCreation.getProcessInstanceKey());
    assertThat(result.getProcessDefinitionKey()).isEqualTo(process.getProcessDefinitionKey());
    assertThat(result.getProcessDefinitionId()).isEqualTo(process.getBpmnProcessId());
    assertThat(result.getProcessDefinitionName()).isEqualTo("my process");
    assertThat(result.getProcessDefinitionVersion()).isEqualTo(1);

    assertThat(result.getTenantId()).isEqualTo("<default>");
    assertThat(result.getTags()).isEqualTo(TAGS);
  }
}
