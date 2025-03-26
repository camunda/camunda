/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.protocol.rest.PageObject;
import io.camunda.client.protocol.rest.PageObject.TypeEnum;
import io.camunda.qa.util.multidb.MultiDbTest;
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

@MultiDbTest
public class ProcessDefinitionSearchTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessDefinitionSearchTest.class);
  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();

  private static CamundaClient camundaClient;

  @BeforeAll
  public static void beforeAll() throws InterruptedException {
    final List<String> forms = List.of("form.form", "form_v2.form");
    forms.forEach(form -> deployResource("form/" + form));

    final List<String> processes =
        List.of(
            "service_tasks_v1.bpmn",
            "service_tasks_v2.bpmn",
            "process_start_form.bpmn",
            "processWithVersionTag.bpmn");

    processes.forEach(
        process -> DEPLOYED_PROCESSES.addAll(deployResource("process/" + process).getProcesses()));

    waitForProcessesToBeDeployed();
  }

  @Test
  void shouldSearchByFromWithLimit() {
    // when
    final var resultAll = camundaClient.newProcessDefinitionSearchRequest().send().join();
    final var thirdKey = resultAll.items().get(2).getProcessDefinitionKey();

    final var resultSearchFrom =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .page(p -> p.limit(2).from(2))
            .send()
            .join();

    // then
    assertThat(resultSearchFrom.items().size()).isEqualTo(2);
    assertThat(resultSearchFrom.items().stream().findFirst().get().getProcessDefinitionKey())
        .isEqualTo(thirdKey);
  }

  @Test
  void shouldPaginateWithSortingByProcessDefinitionKey() {
    // given
    final var resultAll =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionKey().desc())
            .send()
            .join();

    // when
    final var firstPage =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionKey().desc())
            .page(p -> p.limit(1))
            .send()
            .join();

    final var secondPage =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionKey().desc())
            .page(p -> p.limit(1).searchAfter(firstPage.page().lastSortValues()))
            .send()
            .join();

    // then
    assertThat(firstPage.items().size()).isEqualTo(1);
    assertThat(firstPage.items().getFirst().getProcessDefinitionKey())
        .isEqualTo(resultAll.items().get(0).getProcessDefinitionKey());
    assertThat(secondPage.items().size()).isEqualTo(1);
    assertThat(secondPage.items().getFirst().getProcessDefinitionKey())
        .isEqualTo(resultAll.items().get(1).getProcessDefinitionKey());
  }

  @Test
  void shouldPaginateWithSortingByProcessDefinitionId() {
    // given
    final var resultAll =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionId().desc())
            .send()
            .join();

    // when
    final var firstPage =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionId().desc())
            .page(p -> p.limit(2))
            .send()
            .join();

    final var secondPage =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionId().desc())
            .page(p -> p.limit(1).searchAfter(firstPage.page().lastSortValues()))
            .send()
            .join();

    // then
    assertThat(firstPage.items().size()).isEqualTo(2);
    assertThat(firstPage.items().getFirst().getProcessDefinitionKey())
        .isEqualTo(resultAll.items().get(0).getProcessDefinitionKey());
    assertThat(firstPage.items().getLast().getProcessDefinitionKey())
        .isEqualTo(resultAll.items().get(1).getProcessDefinitionKey());
    assertThat(secondPage.items().size()).isEqualTo(1);
    assertThat(secondPage.items().getFirst().getProcessDefinitionKey())
        .isEqualTo(resultAll.items().get(2).getProcessDefinitionKey());
  }

  @Test
  void shouldGetPreviousPageWithSortingByProcessDefinitionId() {
    // given
    final var firstPage =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionId().desc())
            .page(p -> p.limit(2))
            .send()
            .join();

    final var secondPage =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionId().desc())
            .page(p -> p.limit(1).searchAfter(firstPage.page().lastSortValues()))
            .send()
            .join();

    // when
    final var firstPageAgain =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionId().desc())
            .page(p -> p.limit(2).searchBefore(secondPage.page().firstSortValues()))
            .send()
            .join();

    // then
    assertThat(firstPageAgain.items()).isEqualTo(firstPage.items());
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
                camundaClient
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
        camundaClient.newProcessDefinitionGetRequest(processDefinitionKey).send().join();

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
    final var result = camundaClient.newProcessDefinitionSearchRequest().send().join();

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
        camundaClient
            .newProcessDefinitionSearchRequest()
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
        camundaClient.newProcessDefinitionSearchRequest().filter(f -> f.name(name)).send().join();

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
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(f -> f.version(version))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(4);
    assertThat(result.items().getFirst().getVersion()).isEqualTo(version);
  }

  @Test
  void shouldRetrieveProcessDefinitionsByResourceName() {
    // given
    final var resourceName = "process/service_tasks_v1.bpmn";

    // when
    final var result =
        camundaClient
            .newProcessDefinitionSearchRequest()
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
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(f -> f.tenantId(tenantId))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(4);
    assertThat(result.items().getFirst().getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldRetrieveProcessDefinitionsByNullVersionTag() {
    // given
    final String versionTag = null;

    // when
    final var result =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(f -> f.versionTag(versionTag))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(4);
    assertThat(result.items().getFirst().getVersionTag()).isEqualTo(versionTag);
  }

  @Test
  void shouldRetrieveProcessDefinitionsByVersionTag() {
    // given
    final String versionTag = "1.1.0";

    // when
    final var result =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(f -> f.versionTag(versionTag))
            .send()
            .join();

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
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionId().desc())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(4);
    assertThat(result.items().stream().map(ProcessDefinition::getProcessDefinitionId).toList())
        .containsExactlyElementsOf(expectedProcessDefinitionIds);
  }

  @Test
  void shouldSortProcessDefinitionsByKey() {
    // when
    final var resultAsc =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newProcessDefinitionSearchRequest()
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
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionId().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newProcessDefinitionSearchRequest()
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
        camundaClient.newProcessDefinitionSearchRequest().sort(s -> s.name().asc()).send().join();
    final var resultDesc =
        camundaClient.newProcessDefinitionSearchRequest().sort(s -> s.name().desc()).send().join();

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
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.resourceName().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.resourceName().desc())
            .send()
            .join();

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
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.version().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.version().desc())
            .send()
            .join();

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
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.tenantId().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.tenantId().desc())
            .send()
            .join();

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
    final var result =
        camundaClient.newProcessDefinitionSearchRequest().page(p -> p.limit(2)).send().join();
    assertThat(result.items().size()).isEqualTo(2);
    final var key = result.items().getFirst().getProcessDefinitionKey();
    // apply searchAfter
    final var resultAfter =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .page(
                p ->
                    p.searchAfter(
                        Collections.singletonList(
                            new PageObject().type(TypeEnum.INT64).value(String.valueOf(key)))))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(3);
    final var keyAfter = resultAfter.items().getFirst().getProcessDefinitionKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .page(
                p ->
                    p.searchBefore(
                        Collections.singletonList(
                            new PageObject().type(TypeEnum.INT64).value(String.valueOf(keyAfter)))))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(resultBefore.items().getFirst().getProcessDefinitionKey()).isEqualTo(key);
  }

  @Test
  public void shouldValidateGetProcessForm() {
    final var resultProcess =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(f -> f.name("Process With Form"))
            .send()
            .join();

    final var processDefinitionKey =
        resultProcess.items().stream().findFirst().get().getProcessDefinitionKey();

    final var resultForm =
        camundaClient.newProcessDefinitionGetFormRequest(processDefinitionKey).send().join();

    assertThat(resultForm.getFormId()).isEqualTo("test");
    assertThat(resultForm.getVersion()).isEqualTo(2L);
  }

  private static DeploymentEvent deployResource(final String resourceName) {
    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private static void waitForProcessesToBeDeployed() throws InterruptedException {
    Awaitility.await("should deploy processes and import in Operate")
        .atMost(Duration.ofMinutes(5))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newProcessDefinitionSearchRequest().send().join();
              assertThat(result.items().size()).isEqualTo(DEPLOYED_PROCESSES.size());

              final var processDefinitionKey =
                  camundaClient
                      .newProcessDefinitionSearchRequest()
                      .filter(f -> f.name("Process With Form"))
                      .send()
                      .join()
                      .items()
                      .get(0)
                      .getProcessDefinitionKey();

              final var resultForm =
                  camundaClient
                      .newProcessDefinitionGetFormRequest(processDefinitionKey)
                      .send()
                      .join();

              assertThat(resultForm.getFormId().equals("test"));
              assertEquals(2L, resultForm.getVersion());
            });
  }
}
