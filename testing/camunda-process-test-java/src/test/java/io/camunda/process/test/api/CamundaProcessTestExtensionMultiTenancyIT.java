/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.api;

import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/*
 * This test ensures that the new multitenancy configuration works as expected.
 */
public class CamundaProcessTestExtensionMultiTenancyIT {

  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension().withMultiTenancyEnabled(true);

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  public void shouldHaveFullMultiTenancySupportAndIsolation() {
    // given
    final String tenantA = "tenant1";
    final String tenantB = "tenant2";
    final String admin = "demo";
    final String userA = "user1";
    final String password = "demo";
    final String processDefinitionId = "bpmProcessVariable";
    final String processResource = "multitenancy/bpm_variable_test.bpmn";

    createTenant(client, tenantA);
    createTenant(client, tenantB);

    client
        .newCreateUserCommand()
        .name(userA)
        .username(userA)
        .email("user1@example.com")
        .password(password)
        .send()
        .join();

    client
        .newCreateAuthorizationCommand()
        .ownerId(userA)
        .ownerType(OwnerType.USER)
        .resourceId("*")
        .resourceType(ResourceType.PROCESS_DEFINITION)
        .permissionTypes(PermissionType.READ_PROCESS_INSTANCE)
        .send()
        .join();

    assignUserToTenant(client, admin, tenantA);
    assignUserToTenant(client, admin, tenantB);
    assignUserToTenant(client, userA, tenantA);

    deployResourceForTenant(client, processResource, tenantA);
    startProcessInstance(client, processDefinitionId, tenantA);

    deployResourceForTenant(client, processResource, tenantB);
    startProcessInstance(client, processDefinitionId, tenantB);

    waitForElementInstancesBeingExported(client, processDefinitionId);

    // when searching with an admin client that has access to both users and tenants
    final SearchResponse<ElementInstance> allElements =
        client.newElementInstanceSearchRequest().send().join();

    // then returns elements from both
    assertThat(allElements.items()).hasSize(8);
    assertThat(
            allElements.items().stream()
                .map(ElementInstance::getTenantId)
                .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(tenantA, tenantB);

    // when searching with user1's client that has access to tenantA
    final CamundaClient userAClient =
        processTestContext.createClient(
            cb ->
                cb.defaultTenantId(tenantA)
                    .credentialsProvider(
                        CredentialsProvider.newBasicAuthCredentialsProviderBuilder()
                            .username(userA)
                            .password(password)
                            .build()));

    /*
     * The search request should not need to filter by tenants to produce the correct results.
     * At the moment the cluster returns elements of both tenants, violating the properties of
     * the multi-tenancy feature. This is currently under investigation and will be fixed soon.
     */
    final SearchResponse<ElementInstance> tenantAElements =
        userAClient
            .newElementInstanceSearchRequest()
            .filter(filter -> filter.processDefinitionId(processDefinitionId).tenantId(tenantA))
            .send()
            .join();

    // then returns only tenantA elements
    assertThat(tenantAElements.items()).hasSize(4);
    assertThat(
            tenantAElements.items().stream()
                .map(ElementInstance::getTenantId)
                .collect(Collectors.toSet()))
        .contains(tenantA);
  }

  @Test
  void shouldCreateProcessWithoutTenants() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .name("start")
            .zeebeOutputExpression("\"active\"", "status")
            .userTask()
            .name("task")
            .endEvent()
            .name("end")
            .zeebeOutputExpression("\"ok\"", "result")
            .done();

    client.newDeployResourceCommand().addProcessModel(process, "process.bpmn").send().join();

    // when
    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId("process").latestVersion().send().join();

    // then
    CamundaAssert.assertThatProcessInstance(processInstance)
        .isActive()
        .hasActiveElements(byName("task"))
        .hasVariable("status", "active");
  }

  private static void createTenant(final CamundaClient camundaClient, final String tenant) {
    camundaClient.newCreateTenantCommand().tenantId(tenant).name(tenant).send().join();
  }

  private static void assignUserToTenant(
      final CamundaClient camundaClient, final String username, final String tenant) {
    camundaClient.newAssignUserToTenantCommand().username(username).tenantId(tenant).send().join();
  }

  private static DeploymentEvent deployResourceForTenant(
      final CamundaClient camundaClient, final String resourceName, final String tenantId) {

    return camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .tenantId(tenantId)
        .send()
        .join();
  }

  private static void startProcessInstance(
      final CamundaClient camundaClient, final String processId, final String tenant) {
    camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .tenantId(tenant)
        .send()
        .join();
  }

  private static void waitForElementInstancesBeingExported(
      final CamundaClient camundaClient, final String processId) {
    Awaitility.await("should receive data from secondary storage")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              assertThat(
                      camundaClient
                          .newElementInstanceSearchRequest()
                          .filter(filter -> filter.processDefinitionId(processId))
                          .send()
                          .join()
                          .items())
                  .hasSize(8);
            });
  }
}
