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
package io.camunda;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"camunda.process-test.multi-tenancy-enabled=true"})
@CamundaSpringProcessTest
public class MultiTenancyTest {

  private static final String DEFAULT_USERNAME = "demo";

  private static final String TENANT_ID_1 = "tenant-1";
  private static final String TENANT_ID_2 = "tenant-2";

  @Autowired private CamundaClient client;
  @Autowired private CamundaProcessTestContext processTestContext;

  private CamundaClient clientForTenant1;

  @BeforeEach
  void setupTenants() {
    // create tenants
    client.newCreateTenantCommand().tenantId(TENANT_ID_1).name(TENANT_ID_1).send().join();
    client.newCreateTenantCommand().tenantId(TENANT_ID_2).name(TENANT_ID_2).send().join();

    // assign the default user to the tenants
    client
        .newAssignUserToTenantCommand()
        .username(DEFAULT_USERNAME)
        .tenantId(TENANT_ID_1)
        .send()
        .join();
    client
        .newAssignUserToTenantCommand()
        .username(DEFAULT_USERNAME)
        .tenantId(TENANT_ID_2)
        .send()
        .join();

    // create a client for tenant 1
    clientForTenant1 =
        processTestContext.createClient(
            clientBuilder -> clientBuilder.defaultTenantId(TENANT_ID_1));
  }

  @Test
  void createProcessInstance() {
    // given
    clientForTenant1
        .newDeployResourceCommand()
        .addResourceFromClasspath("bpmn/order-process.bpmn")
        .send()
        .join();

    // when
    final var processInstance =
        clientForTenant1
            .newCreateInstanceCommand()
            .bpmnProcessId("order-process")
            .latestVersion()
            .variable("order_id", "order-1")
            .send()
            .join();

    // then
    assertThatProcessInstance(processInstance).isCreated();

    Assertions.assertThat(processInstance.getTenantId()).isEqualTo(TENANT_ID_1);
  }
}
