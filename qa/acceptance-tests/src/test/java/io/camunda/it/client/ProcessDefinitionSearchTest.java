/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitForStartFormsBeingExported;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.sort.ProcessDefinitionSort;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest
public class ProcessDefinitionSearchTest {
  private static final String PROCESS_ID_WITH_START_FORM = "Process_11hxie4";
  private static final String FORM_ID = "test";
  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  private static final List<ProcessDefinitionTestContext> PROCESSES_IN_DEFAULT_TENANT =
      List.of(
          new ProcessDefinitionTestContext(
              "processA_ID", "process/processA-v1.bpmn", 1, "<default>"),
          new ProcessDefinitionTestContext(
              "processA_ID", "process/processA-v2.bpmn", 2, "<default>"),
          new ProcessDefinitionTestContext(
              "processA_ID", "process/processA-v3.bpmn", 3, "<default>"),
          new ProcessDefinitionTestContext(
              "processB_ID", "process/processB-v1.bpmn", 1, "<default>"),
          new ProcessDefinitionTestContext(
              "processB_ID", "process/processB-v2.bpmn", 2, "<default>"),
          new ProcessDefinitionTestContext(
              "service_tasks_v1", "process/service_tasks_v1.bpmn", 1, "<default>"),
          new ProcessDefinitionTestContext(
              "service_tasks_v2", "process/service_tasks_v2.bpmn", 1, "<default>"),
          new ProcessDefinitionTestContext(
              "processWithVersionTag", "process/processWithVersionTag.bpmn", 1, "<default>"),
          new ProcessDefinitionTestContext(
              "Process_11hxie4", "process/process_start_form.bpmn", 1, "<default>"));

  private static CamundaClient camundaClient;

  @BeforeAll
  public static void beforeAll() throws InterruptedException {
    final List<String> forms = List.of("form.form", "form_v2.form");
    forms.forEach(form -> deployResource(camundaClient, "form/" + form));

    PROCESSES_IN_DEFAULT_TENANT.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(camundaClient, process.resourceName()).getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, PROCESSES_IN_DEFAULT_TENANT.size());
    waitForStartFormsBeingExported(camundaClient, PROCESS_ID_WITH_START_FORM, FORM_ID, 2L);
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
  void shouldHaveMoreTotalItemsField() {
    // given
    final var resultAll =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionKey().desc())
            .send()
            .join();

    // then
    assertThat(resultAll.page().hasMoreTotalItems()).isNotNull();
    assertThat(resultAll.page().hasMoreTotalItems()).isFalse();
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
            .page(p -> p.limit(1).after(firstPage.page().endCursor()))
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
            .page(p -> p.limit(1).after(firstPage.page().endCursor()))
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
            .page(p -> p.limit(1).after(firstPage.page().endCursor()))
            .send()
            .join();
    // when
    final var firstPageAgain =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .sort(s -> s.processDefinitionId().desc())
            .page(p -> p.limit(2).before(secondPage.page().startCursor()))
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
        (ProblemException)
            assertThatThrownBy(
                    () ->
                        camundaClient
                            .newProcessDefinitionGetRequest(invalidProcessDefinitionKey)
                            .send()
                            .join())
                .isInstanceOf(ProblemException.class)
                .actual();
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .contains(
            "Process Definition with key '%d' not found".formatted(invalidProcessDefinitionKey));
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
  void shouldGetProcessDefinitionWithFormByKey() {
    // given
    final var processEvent =
        DEPLOYED_PROCESSES.stream()
            .filter(p -> Objects.equals(PROCESS_ID_WITH_START_FORM, p.getBpmnProcessId()))
            .findFirst()
            .orElseThrow();
    final var processDefinitionKey = processEvent.getProcessDefinitionKey();

    // when
    final var result =
        camundaClient.newProcessDefinitionGetRequest(processDefinitionKey).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getHasStartForm()).isTrue();
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
  void shouldRetrieveAllLatestProcessDefinitions() {
    // given
    final var expectedProcessDefinitionInDefaultTenant = keepLatestProcessDefinitionVersions();

    // when
    final var result =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(f -> f.isLatestVersion(true))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(expectedProcessDefinitionInDefaultTenant.size());
    final var results =
        result.items().stream()
            .map(
                pd ->
                    new ProcessDefinitionTestContext(
                        pd.getProcessDefinitionId(),
                        pd.getResourceName(),
                        pd.getVersion(),
                        pd.getTenantId()))
            .toList();
    assertThat(results)
        .containsExactlyInAnyOrderElementsOf(expectedProcessDefinitionInDefaultTenant.values());
  }

  @Test
  void shouldRetrieveSingleLatestProcessDefinitionWhenFilteredById() {
    // given
    final var expectedProcessDefinitionInDefaultTenant =
        keepLatestProcessDefinitionVersions().entrySet().stream()
            .filter(entry -> Objects.equals(entry.getKey(), "processA_ID"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    // when
    final var result =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(f -> f.isLatestVersion(true).processDefinitionId("processA_ID"))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getVersion()).isEqualTo(3);
    final var results =
        result.items().stream()
            .map(
                pd ->
                    new ProcessDefinitionTestContext(
                        pd.getProcessDefinitionId(),
                        pd.getResourceName(),
                        pd.getVersion(),
                        pd.getTenantId()))
            .toList();
    assertThat(results)
        .containsExactlyInAnyOrderElementsOf(expectedProcessDefinitionInDefaultTenant.values());
  }

  @Test
  void shouldRetrieveAllLatestProcessDefinitionsWhenPaginated() {
    // given
    final var expectedProcessDefinitionInDefaultTenant = keepLatestProcessDefinitionVersions();
    // when
    final var processDefinitions = new ArrayList<ProcessDefinition>();
    var endCursor = "";
    do {
      final String finalEndCursor = endCursor;
      final var result =
          camundaClient
              .newProcessDefinitionSearchRequest()
              .filter(f -> f.isLatestVersion(true))
              .page(
                  p -> {
                    p.limit(1);
                    if (!Objects.equals(finalEndCursor, "")) {
                      p.after(finalEndCursor);
                    }
                  })
              .send()
              .join();
      if (!result.items().isEmpty()) {
        processDefinitions.addAll(result.items());
        endCursor = result.page().endCursor();
      } else {
        endCursor = null; // No more items to fetch
      }
    } while (endCursor != null && !endCursor.isEmpty());

    // then
    assertThat(processDefinitions).hasSize(expectedProcessDefinitionInDefaultTenant.size());
    assertThat(
            processDefinitions.stream()
                .map(
                    pd ->
                        new ProcessDefinitionTestContext(
                            pd.getProcessDefinitionId(),
                            pd.getResourceName(),
                            pd.getVersion(),
                            pd.getTenantId()))
                .toList())
        .containsExactlyInAnyOrderElementsOf(expectedProcessDefinitionInDefaultTenant.values());
  }

  private static Stream<Arguments> sortOrderWithComparator() {
    return Stream.of(
        Arguments.of(
            (Function<ProcessDefinitionSort, ProcessDefinitionSort>)
                s -> s.processDefinitionId().asc(),
            Comparator.comparing(ProcessDefinitionTestContext::processId)
                .thenComparing(ProcessDefinitionTestContext::tenantId)),
        Arguments.of(
            (Function<ProcessDefinitionSort, ProcessDefinitionSort>)
                s -> s.processDefinitionId().desc(),
            Comparator.comparing(ProcessDefinitionTestContext::processId)
                .reversed()
                .thenComparing(ProcessDefinitionTestContext::tenantId)));
  }

  @ParameterizedTest
  @MethodSource("sortOrderWithComparator")
  void shouldRetrieveAllLatestProcessDefinitionsWhenPaginatedAndSorted(
      final Function<ProcessDefinitionSort, ProcessDefinitionSort> sort,
      final Comparator<ProcessDefinitionTestContext> comparator) {
    // given
    final var expectedProcessDefinitionInDefaultTenant = keepLatestProcessDefinitionVersions();

    // when
    final var processDefinitions = new LinkedHashSet<ProcessDefinition>();
    var endCursor = "";
    do {
      final String finalEndCursor = endCursor;
      final var result =
          camundaClient
              .newProcessDefinitionSearchRequest()
              .filter(f -> f.isLatestVersion(true))
              .sort(sort::apply)
              .page(
                  p -> {
                    p.limit(1);
                    if (!Objects.equals(finalEndCursor, "")) {
                      p.after(finalEndCursor);
                    }
                  })
              .send()
              .join();
      if (!result.items().isEmpty()) {
        processDefinitions.addAll(result.items());
        endCursor = result.page().endCursor();
      } else {
        endCursor = null; // No more items to fetch
      }
    } while (endCursor != null && !endCursor.isEmpty());

    // then
    assertThat(processDefinitions).hasSize(expectedProcessDefinitionInDefaultTenant.size());
    assertThat(
            processDefinitions.stream()
                .map(
                    pd ->
                        new ProcessDefinitionTestContext(
                            pd.getProcessDefinitionId(),
                            pd.getResourceName(),
                            pd.getVersion(),
                            pd.getTenantId()))
                .toList())
        .containsExactlyElementsOf(
            expectedProcessDefinitionInDefaultTenant.values().stream().sorted(comparator).toList());
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
    assertThat(result.items().size()).isEqualTo(6);
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
    assertThat(result.items().size()).isEqualTo(6);
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
    assertThat(result.items().size()).isEqualTo(9);
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
    assertThat(result.items().size()).isEqualTo(9);
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
    assertThat(result.items().size()).isEqualTo(9);
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
            .page(p -> p.after(result.page().endCursor()))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(7);

    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .page(p -> p.before(resultAfter.page().startCursor()))
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

  private Map<String, ProcessDefinitionTestContext> keepLatestProcessDefinitionVersions() {
    return PROCESSES_IN_DEFAULT_TENANT.stream()
        .collect(
            Collectors.toMap(
                p -> p.processId, p -> p, (p1, p2) -> p1.version >= p2.version ? p1 : p2));
  }

  record ProcessDefinitionTestContext(
      String processId, String resourceName, int version, String tenantId) {
    @Override
    public String toString() {
      return "ProcessTestContext{"
          + "processId='"
          + processId
          + '\''
          + ", version="
          + version
          + ", tenantId='"
          + tenantId
          + '\''
          + '}';
    }
  }
}
