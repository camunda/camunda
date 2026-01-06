/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.startProcessInstanceWithTags;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@CompatibilityTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
public class ProcessInstanceSearchWithTagsTest {

  private static CamundaClient client;
  private static final List<ProcessInstanceEvent> PROCESS_INSTANCES = new ArrayList<>();

  @BeforeAll
  public static void setUp() {
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("my-task", t -> t.zeebeJobType("my-job"))
            .endEvent()
            .done();
    deployProcessAndWaitForIt(client, process, "process.bpmn");

    PROCESS_INSTANCES.add(startProcessInstanceWithTags(client, "process", Set.of("a", "b", "c")));
    PROCESS_INSTANCES.add(startProcessInstanceWithTags(client, "process", Set.of("a", "b")));
    PROCESS_INSTANCES.add(startProcessInstanceWithTags(client, "process", Set.of("a", "c")));
    waitForProcessInstancesToStart(client, PROCESS_INSTANCES.size());
  }

  @AfterAll
  static void afterAll() {
    PROCESS_INSTANCES.clear();
  }

  @Test
  void shouldReturnProcessInstancesWithMatchingTag() {
    final var result =
        client.newProcessInstanceSearchRequest().filter(f -> f.tags("a")).send().join();

    assertThat(result.items())
        .hasSize(3)
        .allSatisfy(
            item -> {
              assertThat(item.getTags()).contains("a");
            });
  }

  @Test
  void shouldReturnNoProcessInstancesWithNotMatchingTags() {
    final var result =
        client.newProcessInstanceSearchRequest().filter(f -> f.tags("a", "d")).send().join();

    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldReturnProcessInstancesWithMatchingTags() {
    final var result =
        client.newProcessInstanceSearchRequest().filter(f -> f.tags("a", "b")).send().join();

    assertThat(result.items())
        .hasSize(2)
        .allSatisfy(
            item -> {
              assertThat(item.getTags()).contains("a", "b");
            });
  }

  @Test
  void shouldReturnProcessInstancesWithMatchingTagsAndOtherFilter() {
    final var result =
        client
            .newProcessInstanceSearchRequest()
            .filter(f -> f.tags("a", "c").processDefinitionId("process"))
            .send()
            .join();

    assertThat(result.items()).isNotEmpty();
    assertThat(result.items())
        .hasSize(2)
        .allSatisfy(
            item -> {
              assertThat(item.getTags()).contains("a", "c");
              assertThat(item.getProcessDefinitionId()).isEqualTo("process");
            });
  }

  @Test
  void shouldReturnNoProcessInstancesWithNotMatchingTagsAndOtherFilter() {
    final var processInstanceKey = PROCESS_INSTANCES.getFirst().getProcessInstanceKey();
    final var result =
        client
            .newProcessInstanceSearchRequest()
            .filter(f -> f.tags("a", "c").processDefinitionId("otherProcess"))
            .send()
            .join();

    assertThat(result.items()).isEmpty();
  }
}
