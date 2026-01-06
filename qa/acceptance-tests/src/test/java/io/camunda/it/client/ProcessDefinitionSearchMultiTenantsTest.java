/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.createTenant;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.deployResourceForTenant;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitForStartFormsBeingExported;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.sort.ProcessDefinitionSort;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.InitializationConfiguration;
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
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ProcessDefinitionSearchMultiTenantsTest {
  @MultiDbTestApplication
  private static final TestCamundaApplication TEST_INSTANCE =
      new TestCamundaApplication().withBasicAuth().withMultiTenancyEnabled();

  private static final String TENANT_ID_1 = "tenant1";
  private static final String USERNAME_1 = "user1";
  private static final String PROCESS_DEFINITION_WITH_START_FORM_ID = "Process_11hxie4";
  private static final String FORM_ID = "test";

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
  private static final List<ProcessDefinitionTestContext> PROCESSES_IN_TENANT_1 =
      List.of(
          new ProcessDefinitionTestContext(
              "processA_ID", "process/processA-v1.bpmn", 1, TENANT_ID_1),
          new ProcessDefinitionTestContext(
              "processA_ID", "process/processA-v3.bpmn", 2, TENANT_ID_1),
          new ProcessDefinitionTestContext(
              "processB_ID", "process/processB-v2.bpmn", 1, TENANT_ID_1),
          new ProcessDefinitionTestContext(
              "service_tasks_v1", "process/service_tasks_v1.bpmn", 1, TENANT_ID_1));

  private static CamundaClient camundaClient;

  @UserDefinition
  private static final TestUser USER_1 = new TestUser(USERNAME_1, "password", List.of());

  @BeforeAll
  public static void beforeAll(@Authenticated final CamundaClient adminClient)
      throws InterruptedException {

    createTenant(
        adminClient,
        TENANT_ID_1,
        TENANT_ID_1,
        InitializationConfiguration.DEFAULT_USER_USERNAME,
        USERNAME_1);

    deployResourceForTenant(camundaClient, "form/form.form", TENANT_ID_1);
    deployResourceForTenant(camundaClient, "form/form_v2.form", TENANT_ID_1);

    final List<String> forms = List.of("form.form", "form_v2.form");
    forms.forEach(form -> deployResource(adminClient, "form/" + form));

    Stream.concat(PROCESSES_IN_DEFAULT_TENANT.stream(), PROCESSES_IN_TENANT_1.stream())
        .forEach(
            process ->
                deployResourceForTenant(adminClient, process.resourceName(), process.tenantId()));

    waitForProcessesToBeDeployed(
        adminClient, PROCESSES_IN_DEFAULT_TENANT.size() + PROCESSES_IN_TENANT_1.size());
    waitForStartFormsBeingExported(
        camundaClient, PROCESS_DEFINITION_WITH_START_FORM_ID, FORM_ID, 2L);
  }

  @Test
  void shouldReturnFormAssignedToDefaultTenant(@Authenticated final CamundaClient camundaClient) {
    // given (user with access to <default> and tenant1)
    final var processDefinitionAssignedToDefaultTenant =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(
                f ->
                    f.processDefinitionId(PROCESS_DEFINITION_WITH_START_FORM_ID)
                        .tenantId("<default>"))
            .page(p -> p.limit(1))
            .send()
            .join()
            .items()
            .getFirst();

    // when
    final var form =
        camundaClient
            .newProcessDefinitionGetFormRequest(
                processDefinitionAssignedToDefaultTenant.getProcessDefinitionKey())
            .send()
            .join();

    // then
    assertThat(form.getTenantId()).isEqualTo("<default>");
  }

  @Test
  void shouldRetrieveAllLatestProcessDefinitions() {
    // given
    final var expectedProcessDefinitionInDefaultTenant =
        keepLatestVersionPerTenant(PROCESSES_IN_DEFAULT_TENANT);
    final var expectedProcessDefinitionsInTenant1 =
        keepLatestVersionPerTenant(PROCESSES_IN_TENANT_1);

    // when
    final var result =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(f -> f.isLatestVersion(true))
            .send()
            .join();

    // then
    assertThat(result.items())
        .hasSize(
            expectedProcessDefinitionInDefaultTenant.size()
                + expectedProcessDefinitionsInTenant1.size());
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
        .containsExactlyInAnyOrderElementsOf(
            Stream.concat(
                    expectedProcessDefinitionInDefaultTenant.values().stream(),
                    expectedProcessDefinitionsInTenant1.values().stream())
                .toList());
  }

  @Test
  void shouldRetrieveAllLatestProcessDefinitionsWhenPaginated() {
    // given
    final var expectedProcessDefinitionInDefaultTenant =
        keepLatestVersionPerTenant(PROCESSES_IN_DEFAULT_TENANT);
    final var expectedProcessDefinitionsInTenant1 =
        keepLatestVersionPerTenant(PROCESSES_IN_TENANT_1);

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
    assertThat(processDefinitions)
        .hasSize(
            expectedProcessDefinitionInDefaultTenant.size()
                + expectedProcessDefinitionsInTenant1.size());
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
        .containsExactlyInAnyOrderElementsOf(
            Stream.concat(
                    expectedProcessDefinitionInDefaultTenant.values().stream(),
                    expectedProcessDefinitionsInTenant1.values().stream())
                .toList());
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
    final var expectedProcessDefinitionInDefaultTenant =
        keepLatestVersionPerTenant(PROCESSES_IN_DEFAULT_TENANT);
    final var expectedProcessDefinitionsInTenant1 =
        keepLatestVersionPerTenant(PROCESSES_IN_TENANT_1);

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
    assertThat(processDefinitions)
        .hasSize(
            expectedProcessDefinitionInDefaultTenant.size()
                + expectedProcessDefinitionsInTenant1.size());
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
            Stream.concat(
                    expectedProcessDefinitionInDefaultTenant.values().stream(),
                    expectedProcessDefinitionsInTenant1.values().stream())
                .sorted(comparator)
                .toList());
  }

  private Map<String, ProcessDefinitionTestContext> keepLatestVersionPerTenant(
      final List<ProcessDefinitionTestContext> processesInDefaultTenant) {
    return processesInDefaultTenant.stream()
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
