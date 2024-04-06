/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ExtendWith(SpringExtension.class)
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
