/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.search.response.ProcessDefinition;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ZeebeIntegration
public class ProcessDefinitionQueryTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessDefinitionQueryTest.class);
  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();

  private static ZeebeClient zeebeClient;

  @TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testStandaloneCamunda;

  static void initTestStandaloneCamunda() {
    testStandaloneCamunda = new TestStandaloneCamunda().withCamundaExporter();
  }

  @BeforeAll
  public static void beforeAll() throws InterruptedException {

    zeebeClient = testStandaloneCamunda.newClientBuilder().build();

    // Deploy form
    deployResource(String.format("form/%s", "form.form"));
    deployResource(String.format("form/%s", "form_v2.form"));

    final List<String> processes =
        List.of(
            "service_tasks_v1.bpmn",
            "service_tasks_v2.bpmn",
            "incident_process_v1.bpmn",
            "manual_process.bpmn",
            "parent_process_v1.bpmn",
            "child_process_v1.bpmn",
            "process_start_form.bpmn",
            "processWithVersionTag.bpmn");
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(String.format("process/%s", process)).getProcesses()));

    waitForProcessesToBeDeployed();
  }

  @Test
  void shouldPaginateByTheLimit() {
    // when
    final var result = zeebeClient.newProcessDefinitionQuery().page(p -> p.limit(2)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
  }

  @Test
  void shouldSearchAfterSecondItem() {
    // when
    final var resultAll = zeebeClient.newProcessDefinitionQuery().send().join();

    final var secondProcessKey = resultAll.items().get(1).getProcessDefinitionKey();
    final var thirdProcessKey = resultAll.items().get(2).getProcessDefinitionKey();

    final var resultSearchAfter =
        zeebeClient
            .newProcessDefinitionQuery()
            .page(p -> p.limit(2).searchAfter(Collections.singletonList(secondProcessKey)))
            .send()
            .join();

    // then
    assertThat(resultSearchAfter.items().stream().findFirst().get().getProcessDefinitionKey())
        .isEqualTo(thirdProcessKey);
  }

  @Test
  void shouldSearchBeforeSecondItem() {
    // when
    final var resultAll = zeebeClient.newProcessDefinitionQuery().send().join();

    final var secondProcessKey = resultAll.items().get(1).getProcessDefinitionKey();
    final var firstProcessKey = resultAll.items().get(0).getProcessDefinitionKey();

    final var resultSearchBefore =
        zeebeClient
            .newProcessDefinitionQuery()
            .page(p -> p.limit(2).searchBefore(Collections.singletonList(secondProcessKey)))
            .send()
            .join();

    // then
    assertThat(resultSearchBefore.items().stream().findFirst().get().getProcessDefinitionKey())
        .isEqualTo(firstProcessKey);
  }

  @Test
  void shouldThrownExceptionIfProcessDefinitionNotFoundByKey() {
    // given
    final long invalidProcessDefinitionKey = 0xC00L;

    // when / then
    final var exception =
        assertThrowsExactly(
            ProblemException.class,
            () ->
                zeebeClient
                    .newProcessDefinitionGetRequest(invalidProcessDefinitionKey)
                    .send()
                    .join());
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .isEqualTo("Process definition with key 3072 not found");
  }

  @Test
  void shouldGetProcessDefinitionByKey() {
    // given
    final var processDefinitionId = "service_tasks_v1";
    final var processEvent =
        DEPLOYED_PROCESSES.stream()
            .filter(p -> Objects.equals(processDefinitionId, p.getBpmnProcessId()))
            .findFirst()
            .orElseThrow();
    final var processDefinitionKey = processEvent.getProcessDefinitionKey();

    // when
    final var result =
        zeebeClient.newProcessDefinitionGetRequest(processDefinitionKey).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(result.getProcessDefinitionId()).isEqualTo(processDefinitionId);
    assertThat(result.getName()).isEqualTo("Service tasks v1");
    assertThat(result.getVersion()).isEqualTo(1);
    assertThat(result.getResourceName()).isEqualTo("process/service_tasks_v1.bpmn");
    assertThat(result.getTenantId()).isEqualTo("<default>");
    assertThat(result.getVersionTag()).isNull();
  }

  @Test
  void shouldRetrieveAllProcessDefinitionsByDefault() {
    // given
    final var expectedProcessDefinitionIds =
        DEPLOYED_PROCESSES.stream().map(Process::getBpmnProcessId).toList();

    // when
    final var result = zeebeClient.newProcessDefinitionQuery().send().join();

    // then
    assertThat(result.items().size()).isEqualTo(expectedProcessDefinitionIds.size());
    assertThat(result.items().stream().map(ProcessDefinition::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrderElementsOf(expectedProcessDefinitionIds);
  }

  @Test
  void shouldRetrieveProcessDefinitionsByProcessDefinitionId() {
    // given
    final var processDefinitionId = "service_tasks_v1";

    // when
    final var result =
        zeebeClient
            .newProcessDefinitionQuery()
            .filter(f -> f.processDefinitionId(processDefinitionId))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo(processDefinitionId);
  }

  @Test
  void shouldRetrieveProcessDefinitionsByName() {
    // given
    final var name = "Service tasks v1";

    // when
    final var result =
        zeebeClient.newProcessDefinitionQuery().filter(f -> f.name(name)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getName()).isEqualTo(name);
  }

  @Test
  void shouldRetrieveProcessDefinitionsByVersion() {
    // given
    final var version = 1;

    // when
    final var result =
        zeebeClient.newProcessDefinitionQuery().filter(f -> f.version(version)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(8);
    assertThat(result.items().getFirst().getVersion()).isEqualTo(version);
  }

  @Test
  void shouldRetrieveProcessDefinitionsByResourceName() {
    // given
    final var resourceName = "process/service_tasks_v1.bpmn";

    // when
    final var result =
        zeebeClient
            .newProcessDefinitionQuery()
            .filter(f -> f.resourceName(resourceName))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getResourceName()).isEqualTo(resourceName);
  }

  @Test
  void shouldRetrieveProcessDefinitionsByTenantId() {
    // given
    final var tenantId = "<default>";

    // when
    final var result =
        zeebeClient.newProcessDefinitionQuery().filter(f -> f.tenantId(tenantId)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(8);
    assertThat(result.items().getFirst().getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldRetrieveProcessDefinitionsByNullVersionTag() {
    // given
    final String versionTag = null;

    // when
    final var result =
        zeebeClient.newProcessDefinitionQuery().filter(f -> f.versionTag(versionTag)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(8);
    assertThat(result.items().getFirst().getVersionTag()).isEqualTo(versionTag);
  }

  @Test
  void shouldRetrieveProcessDefinitionsByVersionTag() {
    // given
    final String versionTag = "1.1.0";

    // when
    final var result =
        zeebeClient.newProcessDefinitionQuery().filter(f -> f.versionTag(versionTag)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getVersionTag()).isEqualTo(versionTag);
  }

  @Test
  void shouldRetrieveProcessDefinitionsWithReverseSorting() {
    // given
    final var expectedProcessDefinitionIds =
        DEPLOYED_PROCESSES.stream()
            .map(Process::getBpmnProcessId)
            .sorted(Comparator.reverseOrder())
            .toList();

    // when
    final var result =
        zeebeClient
            .newProcessDefinitionQuery()
            .sort(s -> s.processDefinitionId().desc())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(8);
    assertThat(result.items().stream().map(ProcessDefinition::getProcessDefinitionId).toList())
        .containsExactlyElementsOf(expectedProcessDefinitionIds);
  }

  @Test
  void shouldSortProcessDefinitionsByKey() {
    // when
    final var resultAsc =
        zeebeClient
            .newProcessDefinitionQuery()
            .sort(s -> s.processDefinitionKey().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newProcessDefinitionQuery()
            .sort(s -> s.processDefinitionKey().desc())
            .send()
            .join();

    final var all =
        resultAsc.items().stream().map(ProcessDefinition::getProcessDefinitionKey).toList();
    final var sortedAsc = all.stream().sorted(Comparator.naturalOrder()).toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(ProcessDefinition::getProcessDefinitionKey).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(ProcessDefinition::getProcessDefinitionKey).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortProcessDefinitionsByProcessDefinitionId() {
    // when
    final var resultAsc =
        zeebeClient
            .newProcessDefinitionQuery()
            .sort(s -> s.processDefinitionId().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newProcessDefinitionQuery()
            .sort(s -> s.processDefinitionId().desc())
            .send()
            .join();

    final var all =
        resultAsc.items().stream().map(ProcessDefinition::getProcessDefinitionId).toList();
    final var sortedAsc = all.stream().sorted(Comparator.naturalOrder()).toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(ProcessDefinition::getProcessDefinitionId).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(ProcessDefinition::getProcessDefinitionId).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortProcessDefinitionsByName() {
    // when
    final var resultAsc =
        zeebeClient.newProcessDefinitionQuery().sort(s -> s.name().asc()).send().join();
    final var resultDesc =
        zeebeClient.newProcessDefinitionQuery().sort(s -> s.name().desc()).send().join();

    final var all = resultAsc.items().stream().map(ProcessDefinition::getName).toList();
    final var sortedAsc = all.stream().sorted(Comparator.naturalOrder()).toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(ProcessDefinition::getName).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(ProcessDefinition::getName).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortProcessDefinitionsByProcessResourceName() {
    // when
    final var resultAsc =
        zeebeClient.newProcessDefinitionQuery().sort(s -> s.resourceName().asc()).send().join();
    final var resultDesc =
        zeebeClient.newProcessDefinitionQuery().sort(s -> s.resourceName().desc()).send().join();

    final var all = resultAsc.items().stream().map(ProcessDefinition::getResourceName).toList();
    final var sortedAsc = all.stream().sorted(Comparator.naturalOrder()).toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(ProcessDefinition::getResourceName).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(ProcessDefinition::getResourceName).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortProcessDefinitionsByVersion() {
    // when
    final var resultAsc =
        zeebeClient.newProcessDefinitionQuery().sort(s -> s.version().asc()).send().join();
    final var resultDesc =
        zeebeClient.newProcessDefinitionQuery().sort(s -> s.version().desc()).send().join();

    final var all = resultAsc.items().stream().map(ProcessDefinition::getVersion).toList();
    final var sortedAsc = all.stream().sorted(Comparator.naturalOrder()).toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(ProcessDefinition::getVersion).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(ProcessDefinition::getVersion).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortProcessDefinitionsByTenantId() {
    // when
    final var resultAsc =
        zeebeClient.newProcessDefinitionQuery().sort(s -> s.tenantId().asc()).send().join();
    final var resultDesc =
        zeebeClient.newProcessDefinitionQuery().sort(s -> s.tenantId().desc()).send().join();

    final var all = resultAsc.items().stream().map(ProcessDefinition::getTenantId).toList();
    final var sortedAsc = all.stream().sorted(Comparator.naturalOrder()).toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(ProcessDefinition::getTenantId).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(ProcessDefinition::getTenantId).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  public void shouldValidatePagination() {
    final var result = zeebeClient.newProcessDefinitionQuery().page(p -> p.limit(2)).send().join();
    assertThat(result.items().size()).isEqualTo(2);
    final var key = result.items().getFirst().getProcessDefinitionKey();
    // apply searchAfter
    final var resultAfter =
        zeebeClient
            .newProcessDefinitionQuery()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(7);
    final var keyAfter = resultAfter.items().getFirst().getProcessDefinitionKey();
    // apply searchBefore
    final var resultBefore =
        zeebeClient
            .newProcessDefinitionQuery()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(resultBefore.items().getFirst().getProcessDefinitionKey()).isEqualTo(key);
  }

  @Test
  public void shouldValidateGetProcessForm() {
    final var resultProcess =
        zeebeClient
            .newProcessDefinitionQuery()
            .filter(f -> f.name("Process With Form"))
            .send()
            .join();

    final var processDefinitionKey =
        resultProcess.items().stream().findFirst().get().getProcessDefinitionKey();

    final var resultForm =
        zeebeClient.newProcessDefinitionGetFormRequest(processDefinitionKey).send().join();

    assertThat(resultForm.getFormId()).isEqualTo("test");
    assertThat(resultForm.getVersion()).isEqualTo(2L);
  }

  private static DeploymentEvent deployResource(final String resourceName) {
    return zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private static void waitForProcessesToBeDeployed() throws InterruptedException {
    Awaitility.await("should deploy processes and import in Operate")
        .atMost(Duration.ofMinutes(3))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newProcessDefinitionQuery().send().join();
              assertThat(result.items().size()).isEqualTo(DEPLOYED_PROCESSES.size());

              final var processDefinitionKey =
                  zeebeClient
                      .newProcessDefinitionQuery()
                      .filter(f -> f.name("Process With Form"))
                      .send()
                      .join()
                      .items()
                      .get(0)
                      .getProcessDefinitionKey();

              final var resultForm =
                  zeebeClient
                      .newProcessDefinitionGetFormRequest(processDefinitionKey)
                      .send()
                      .join();

              assertThat(resultForm.getFormId().equals("test"));
            });
  }
}
