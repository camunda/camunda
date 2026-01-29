/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.mt;

import static io.camunda.tasklist.qa.util.TestContainerUtil.TENANT_1;
import static io.camunda.tasklist.qa.util.TestContainerUtil.TENANT_2;
import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.util.IdentityTester;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskAssignRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class MultiTenancyIT extends IdentityTester {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  private MockMvcHelper mockMvcHelper;

  @DynamicPropertySource
  protected static void registerProperties(final DynamicPropertyRegistry registry) {
    IdentityTester.registerProperties(registry, true);
  }

  @BeforeAll
  public static void beforeClass() {
    IdentityTester.beforeClass(true);
  }

  @BeforeEach
  public void setUp() {
    super.before();
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void searchProcessesShouldReturnOnlyUserTenantsOwnedProcesses() {
    createAuthorization(getUserId(0), "USER", "*", "process-definition", "START_PROCESS_INSTANCE");
    createAuthorization(getUserId(1), "USER", "*", "process-definition", "START_PROCESS_INSTANCE");

    // tenant 1
    final String processId1 =
        tester
            .deployProcessForTenant(TENANT_1, "simple_process.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();
    // tenant 2
    final String processId2 =
        tester
            .deployProcessForTenant(TENANT_2, "simple_process.bpmn")
            .waitUntil()
            .processIsDeployed()
            .getProcessDefinitionKey();

    final var result1 = mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1));
    // demo user is part of tenant1 and tenant2
    assertThat(result1)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .hasSize(2)
        .extracting("id", "tenantId")
        .containsExactlyInAnyOrder(tuple(processId1, TENANT_1), tuple(processId2, TENANT_2));

    tester.withAuthenticationToken(generateTokenForUser(USER_2));
    final var result2 = mockMvcHelper.doRequest(get(TasklistURIs.PROCESSES_URL_V1));
    // user2 is part of tenant2 only
    assertThat(result2)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingListContent(objectMapper, ProcessResponse.class)
        .singleElement()
        .satisfies(
            process -> {
              Assertions.assertThat(process.getId()).isEqualTo(processId2);
              Assertions.assertThat(process.getTenantId()).isEqualTo(TENANT_2);
            });
  }

  @Test
  public void canAssignOnlyUserTenantsTasks() throws Exception {
    // given
    final String bpmnProcessId = "simpleTestProcess";
    final String flowNodeBpmnId = "taskD_".concat(UUID.randomUUID().toString());

    final String taskId =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId, TENANT_1)
            .then()
            .processIsDeployed()
            .and()
            .startProcessInstance(TENANT_1, bpmnProcessId, null)
            .then()
            .taskIsCreated(flowNodeBpmnId)
            .getTaskId();

    tester.withAuthenticationToken(generateTokenForUser(USER_2));

    // user2 cannot assign task(tenant1) as they are part of tenant1
    final var assignRequest = new TaskAssignRequest().setAssignee("john_smith");

    final var errorResult =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId), assignRequest);

    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage("task with id %s was not found".formatted(taskId));

    tester.withAuthenticationToken(generateTokenForUser(USER));

    // user1 can assign task(tenant1) as they are part of tenant1
    final var result =
        mockMvcHelper.doRequest(
            patch(TasklistURIs.TASKS_URL_V1.concat("/{taskId}/assign"), taskId), assignRequest);

    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, TaskResponse.class)
        .satisfies(
            task -> {
              Assertions.assertThat(task.getId()).isEqualTo(taskId);
              Assertions.assertThat(task.getAssignee()).isEqualTo("john_smith");
              Assertions.assertThat(task.getCreationDate()).isNotNull();
              Assertions.assertThat(task.getCompletionDate()).isNull();
            });
  }

  @Test
  public void shouldReturnFormFromSameTenantOfProcessDefinition() throws Exception {
    // Deploy two versions of the form for tenant 1
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("formDeployedV1.form")
        .tenantId(TENANT_1)
        .send()
        .join();
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("formDeployedV2.form")
        .tenantId(TENANT_1)
        .send()
        .join();

    // Deploy two versions of the form for tenant 2,
    // with the latest one being different from the one in tenant 1
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("formDeployedV1.form")
        .tenantId(TENANT_2)
        .send()
        .join();
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("formDeployedV3.form")
        .tenantId(TENANT_2)
        .send()
        .join();

    // Start a process referencing the form for tenant 1
    final String processDefinitionKey1 =
        tester
            .deployProcessForTenant(TENANT_1, "formIdProcessDeployed.bpmn")
            .processIsDeployed()
            .startProcessInstance(TENANT_1, "Process_11hxie4", null)
            .taskIsCreated("Activity_14emqkd")
            .getProcessDefinitionKey();

    // Start a process referencing the form for tenant 3
    final String processDefinitionKey2 =
        tester
            .deployProcessForTenant(TENANT_2, "formIdProcessDeployed.bpmn")
            .processIsDeployed()
            .startProcessInstance(TENANT_2, "Process_11hxie4", null)
            .taskIsCreated("Activity_14emqkd")
            .getProcessDefinitionKey();

    // When fetching the form for the first process, the form from tenant 1 should be returned
    final var tenant1Result =
        mockMvcHelper.doRequest(
            get(TasklistURIs.FORMS_URL_V1.concat("/Form_0mik7px"))
                .param("processDefinitionKey", processDefinitionKey1)
                .param("version", "2"));
    assertThat(tenant1Result)
        .hasOkHttpStatus()
        .extractingContent(objectMapper, FormResponse.class)
        .satisfies(form -> Assertions.assertThat(form.getTenantId()).isEqualTo(TENANT_1));

    // When fetching the form for the second process, the form from tenant 2 should be returned
    final var tenant2Result =
        mockMvcHelper.doRequest(
            get(TasklistURIs.FORMS_URL_V1.concat("/Form_0mik7px"))
                .param("processDefinitionKey", processDefinitionKey2)
                .param("version", "2"));
    assertThat(tenant2Result)
        .hasOkHttpStatus()
        .extractingContent(objectMapper, FormResponse.class)
        .satisfies(form -> Assertions.assertThat(form.getTenantId()).isEqualTo(TENANT_2));
  }
}
