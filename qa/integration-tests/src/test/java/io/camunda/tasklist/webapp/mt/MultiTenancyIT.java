/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.mt;

import static io.camunda.tasklist.qa.util.TestContainerUtil.TENANT_1;
import static io.camunda.tasklist.qa.util.TestContainerUtil.TENANT_2;
import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.IdentityTester;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskAssignRequest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + "importer.jobType = testJobType"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@ActiveProfiles({TasklistProfileService.IDENTITY_AUTH_PROFILE, "test"})
public class MultiTenancyIT extends IdentityTester {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  private MockMvcHelper mockMvcHelper;

  @DynamicPropertySource
  protected static void registerProperties(DynamicPropertyRegistry registry) {
    IdentityTester.registerProperties(registry, true);
  }

  @BeforeClass
  public static void beforeClass() {
    IdentityTester.beforeClass(true);
  }

  @Before
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
        .containsExactly(tuple(processId1, TENANT_1), tuple(processId2, TENANT_2));

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
}
