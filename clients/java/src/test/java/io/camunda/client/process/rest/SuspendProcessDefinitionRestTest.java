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
package io.camunda.client.process.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.SuspendProcessDefinitionResponse;
import io.camunda.client.api.response.SuspendProcessDefinitionVersionResult.Outcome;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.ProcessDefinitionSuspensionInstruction;
import io.camunda.client.protocol.rest.ProcessDefinitionSuspensionResult;
import io.camunda.client.protocol.rest.ProcessDefinitionSuspensionVersionResult;
import io.camunda.client.protocol.rest.ProcessDefinitionSuspensionVersionResult.OutcomeEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import org.junit.jupiter.api.Test;

public class SuspendProcessDefinitionRestTest extends ClientRestTest {

  private static final long PROCESS_DEFINITION_KEY = 123L;
  private static final String PROCESS_DEFINITION_ID = "order-process";

  @Test
  void shouldSuspendByKey() {
    // given
    gatewayService.onSuspendProcessDefinitionsRequest(
        new ProcessDefinitionSuspensionResult()
            .addProcessDefinitionsItem(
                new ProcessDefinitionSuspensionVersionResult()
                    .processDefinitionKey(String.valueOf(PROCESS_DEFINITION_KEY))
                    .processDefinitionVersion(3)
                    .outcome(OutcomeEnum.SUSPENDED)));

    // when
    final SuspendProcessDefinitionResponse response =
        client
            .newSuspendProcessDefinitionCommand()
            .processDefinitionKey(PROCESS_DEFINITION_KEY)
            .send()
            .join();

    // then
    final ProcessDefinitionSuspensionInstruction request =
        gatewayService.getLastRequest(ProcessDefinitionSuspensionInstruction.class);
    assertThat(request.getProcessDefinitionKey()).isEqualTo(String.valueOf(PROCESS_DEFINITION_KEY));
    assertThat(request.getProcessDefinitionId()).isNull();
    assertThat(request.getTenantId()).isNull();
    assertThat(request.getIncludeProcessInstances()).isFalse();
    assertThat(response.getBatchOperationKey()).isNull();
    assertThat(response.getProcessDefinitions()).hasSize(1);
    assertThat(response.getProcessDefinitions().get(0).getProcessDefinitionKey())
        .isEqualTo(PROCESS_DEFINITION_KEY);
    assertThat(response.getProcessDefinitions().get(0).getProcessDefinitionVersion()).isEqualTo(3);
    assertThat(response.getProcessDefinitions().get(0).getOutcome()).isEqualTo(Outcome.SUSPENDED);
  }

  @Test
  void shouldSuspendByKeyWithIncludeProcessInstances() {
    // given
    gatewayService.onSuspendProcessDefinitionsRequest(
        new ProcessDefinitionSuspensionResult()
            .addProcessDefinitionsItem(
                new ProcessDefinitionSuspensionVersionResult()
                    .processDefinitionKey(String.valueOf(PROCESS_DEFINITION_KEY))
                    .processDefinitionVersion(3)
                    .outcome(OutcomeEnum.ALREADY_SUSPENDED))
            .batchOperationKey("batch-1"));

    // when
    final SuspendProcessDefinitionResponse response =
        client
            .newSuspendProcessDefinitionCommand()
            .processDefinitionKey(PROCESS_DEFINITION_KEY)
            .includeProcessInstances(true)
            .send()
            .join();

    // then
    final ProcessDefinitionSuspensionInstruction request =
        gatewayService.getLastRequest(ProcessDefinitionSuspensionInstruction.class);
    assertThat(request.getIncludeProcessInstances()).isTrue();
    assertThat(response.getBatchOperationKey()).isEqualTo("batch-1");
    assertThat(response.getProcessDefinitions().get(0).getOutcome())
        .isEqualTo(Outcome.ALREADY_SUSPENDED);
  }

  @Test
  void shouldSuspendAllVersionsById() {
    // given
    gatewayService.onSuspendProcessDefinitionsRequest(
        new ProcessDefinitionSuspensionResult()
            .addProcessDefinitionsItem(
                new ProcessDefinitionSuspensionVersionResult()
                    .processDefinitionKey("1")
                    .processDefinitionVersion(1)
                    .outcome(OutcomeEnum.SUSPENDED))
            .addProcessDefinitionsItem(
                new ProcessDefinitionSuspensionVersionResult()
                    .processDefinitionKey("2")
                    .processDefinitionVersion(2)
                    .outcome(OutcomeEnum.DRAINING)));

    // when
    final SuspendProcessDefinitionResponse response =
        client
            .newSuspendProcessDefinitionCommand()
            .processDefinitionId(PROCESS_DEFINITION_ID)
            .send()
            .join();

    // then
    final ProcessDefinitionSuspensionInstruction request =
        gatewayService.getLastRequest(ProcessDefinitionSuspensionInstruction.class);
    assertThat(request.getProcessDefinitionId()).isEqualTo(PROCESS_DEFINITION_ID);
    assertThat(request.getProcessDefinitionVersion()).isNull();
    assertThat(request.getProcessDefinitionKey()).isNull();
    assertThat(request.getTenantId()).isEqualTo(client.getConfiguration().getDefaultTenantId());
    assertThat(response.getProcessDefinitions())
        .extracting(r -> r.getOutcome())
        .containsExactly(Outcome.SUSPENDED, Outcome.DRAINING);
  }

  @Test
  void shouldSuspendByIdAndVersionWithTenantAndIncludeProcessInstances() {
    // given
    gatewayService.onSuspendProcessDefinitionsRequest(
        new ProcessDefinitionSuspensionResult().batchOperationKey("batch-2"));

    // when
    final SuspendProcessDefinitionResponse response =
        client
            .newSuspendProcessDefinitionCommand()
            .processDefinitionId(PROCESS_DEFINITION_ID)
            .version(2)
            .tenantId("tenant-a")
            .includeProcessInstances(true)
            .send()
            .join();

    // then
    final ProcessDefinitionSuspensionInstruction request =
        gatewayService.getLastRequest(ProcessDefinitionSuspensionInstruction.class);
    assertThat(request.getProcessDefinitionId()).isEqualTo(PROCESS_DEFINITION_ID);
    assertThat(request.getProcessDefinitionVersion()).isEqualTo(2);
    assertThat(request.getTenantId()).isEqualTo("tenant-a");
    assertThat(request.getIncludeProcessInstances()).isTrue();
    assertThat(response.getBatchOperationKey()).isEqualTo("batch-2");
  }

  @Test
  void shouldHandleMissingProcessDefinitionsInResponse() {
    // given
    gatewayService.onSuspendProcessDefinitionsRequest(
        new ProcessDefinitionSuspensionResult().processDefinitions(null));

    // when
    final SuspendProcessDefinitionResponse response =
        client
            .newSuspendProcessDefinitionCommand()
            .processDefinitionKey(PROCESS_DEFINITION_KEY)
            .send()
            .join();

    // then
    assertThat(response.getProcessDefinitions()).isEmpty();
  }

  @Test
  void shouldMapUnknownOutcomeToUnknownEnumValue() {
    // given
    gatewayService.onSuspendProcessDefinitionsRequest(
        new ProcessDefinitionSuspensionResult()
            .addProcessDefinitionsItem(
                new ProcessDefinitionSuspensionVersionResult()
                    .processDefinitionKey("1")
                    .processDefinitionVersion(1)
                    .outcome(OutcomeEnum.UNKNOWN_DEFAULT_OPEN_API)));

    // when
    final SuspendProcessDefinitionResponse response =
        client.newSuspendProcessDefinitionCommand().processDefinitionKey(1L).send().join();

    // then
    assertThat(response.getProcessDefinitions().get(0).getOutcome())
        .isEqualTo(Outcome.UNKNOWN_ENUM_VALUE);
  }

  @Test
  void shouldRejectNullOrEmptyProcessDefinitionId() {
    assertThatThrownBy(() -> client.newSuspendProcessDefinitionCommand().processDefinitionId(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionId");
    assertThatThrownBy(() -> client.newSuspendProcessDefinitionCommand().processDefinitionId(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionId");
  }

  @Test
  void shouldRejectNonPositiveVersion() {
    assertThatThrownBy(
            () ->
                client
                    .newSuspendProcessDefinitionCommand()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .version(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }

  @Test
  void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getSuspendProcessDefinitionsUrl(),
        () -> new ProblemDetail().title("Draining definition").status(409));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newSuspendProcessDefinitionCommand()
                    .processDefinitionKey(PROCESS_DEFINITION_KEY)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Draining definition");
  }
}
