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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies that the cluster is purged between test methods when basic authorization is enabled via
 * environment variables. This test specifically covers the scenario described in the issue where
 * the purge completion check failed with an authentication error when auth was enabled, causing a
 * spurious 30-second timeout (even though the purge itself succeeded).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CamundaProcessTestExtensionBasicAuthPurgeIT {

  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask().endEvent().done();

  // Enable basic authorization using the managed runtime's multi-tenancy configuration, which
  // internally sets CAMUNDA_SECURITY_AUTHENTICATION_UNPROTECTEDAPI=false and
  // ZEEBE_GATEWAY_SECURITY_AUTHENTICATION_MODE=basic on the Camunda container. This is the
  // established way to enable authentication on a managed CPT runtime. The configured
  // CamundaClient (injected below) already carries the demo/demo credentials and will be
  // used for both test requests and purge completion checks.
  @RegisterExtension
  private static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension().withMultiTenancyEnabled(true);

  // to be injected
  private CamundaClient client;

  @Test
  @Order(1)
  void shouldCreateProcessInstance() {
    // given
    client.newDeployResourceCommand().addProcessModel(PROCESS, "process.bpmn").send().join();

    // when
    final ProcessInstanceEvent processInstance =
        client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();

    // then
    Awaitility.await("Wait until process instance is exported to secondary storage")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final List<ProcessInstance> instances =
                  client.newProcessInstanceSearchRequest().send().join().items();
              assertThat(instances)
                  .hasSize(1)
                  .extracting(ProcessInstance::getProcessInstanceKey)
                  .contains(processInstance.getProcessInstanceKey());
            });
  }

  @Test
  @Order(2)
  void shouldStartWithCleanClusterAfterPurge() {
    // The cluster is purged automatically after each test method. If the purge failed (or timed
    // out because the completion check couldn't authenticate with the runtime), the previous
    // test's process instance would still be present here.
    assertThat(client.newProcessInstanceSearchRequest().send().join().items()).isEmpty();
  }
}
