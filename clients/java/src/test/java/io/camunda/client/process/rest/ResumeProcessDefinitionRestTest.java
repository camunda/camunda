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
import io.camunda.client.api.response.ResumeProcessDefinitionResponse;
import io.camunda.client.api.response.ResumeProcessDefinitionVersionResult.Outcome;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.ProcessDefinitionResumptionInstruction;
import io.camunda.client.protocol.rest.ProcessDefinitionResumptionResult;
import io.camunda.client.protocol.rest.ProcessDefinitionResumptionVersionResult;
import io.camunda.client.protocol.rest.ProcessDefinitionResumptionVersionResult.OutcomeEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import org.junit.jupiter.api.Test;

public class ResumeProcessDefinitionRestTest extends ClientRestTest {

  private static final long PROCESS_DEFINITION_KEY = 123L;
  private static final String PROCESS_DEFINITION_ID = "order-process";

  @Test
  void shouldResumeByKey() {
    // given
    gatewayService.onResumeProcessDefinitionsRequest(
        new ProcessDefinitionResumptionResult()
            .addProcessDefinitionsItem(
                new ProcessDefinitionResumptionVersionResult()
                    .processDefinitionKey(String.valueOf(PROCESS_DEFINITION_KEY))
                    .processDefinitionVersion(3)
                    .outcome(OutcomeEnum.RESUMED)));

    // when
    final ResumeProcessDefinitionResponse response =
        client
            .newResumeProcessDefinitionCommand()
            .processDefinitionKey(PROCESS_DEFINITION_KEY)
            .send()
            .join();

    // then
    final ProcessDefinitionResumptionInstruction request =
        gatewayService.getLastRequest(ProcessDefinitionResumptionInstruction.class);
    assertThat(request.getProcessDefinitionKey()).isEqualTo(String.valueOf(PROCESS_DEFINITION_KEY));
    assertThat(request.getProcessDefinitionId()).isNull();
    assertThat(request.getTenantId()).isNull();
    assertThat(request.getIncludeProcessInstances()).isFalse();
    assertThat(response.getBatchOperationKey()).isNull();
    assertThat(response.getProcessDefinitions()).hasSize(1);
    assertThat(response.getProcessDefinitions().get(0).getProcessDefinitionKey())
        .isEqualTo(PROCESS_DEFINITION_KEY);
    assertThat(response.getProcessDefinitions().get(0).getProcessDefinitionVersion()).isEqualTo(3);
    assertThat(response.getProcessDefinitions().get(0).getOutcome()).isEqualTo(Outcome.RESUMED);
  }

  @Test
  void shouldResumeByKeyWithIncludeProcessInstances() {
    // given
    gatewayService.onResumeProcessDefinitionsRequest(
        new ProcessDefinitionResumptionResult()
            .addProcessDefinitionsItem(
                new ProcessDefinitionResumptionVersionResult()
                    .processDefinitionKey(String.valueOf(PROCESS_DEFINITION_KEY))
                    .processDefinitionVersion(3)
                    .outcome(OutcomeEnum.ALREADY_RESUMED))
            .batchOperationKey("batch-1"));

    // when
    final ResumeProcessDefinitionResponse response =
        client
            .newResumeProcessDefinitionCommand()
            .processDefinitionKey(PROCESS_DEFINITION_KEY)
            .includeProcessInstances(true)
            .send()
            .join();

    // then
    final ProcessDefinitionResumptionInstruction request =
        gatewayService.getLastRequest(ProcessDefinitionResumptionInstruction.class);
    assertThat(request.getIncludeProcessInstances()).isTrue();
    assertThat(response.getBatchOperationKey()).isEqualTo("batch-1");
    assertThat(response.getProcessDefinitions().get(0).getOutcome())
        .isEqualTo(Outcome.ALREADY_RESUMED);
  }

  @Test
  void shouldResumeAllVersionsById() {
    // given
    gatewayService.onResumeProcessDefinitionsRequest(
        new ProcessDefinitionResumptionResult()
            .addProcessDefinitionsItem(
                new ProcessDefinitionResumptionVersionResult()
                    .processDefinitionKey("1")
                    .processDefinitionVersion(1)
                    .outcome(OutcomeEnum.RESUMED))
            .addProcessDefinitionsItem(
                new ProcessDefinitionResumptionVersionResult()
                    .processDefinitionKey("2")
                    .processDefinitionVersion(2)
                    .outcome(OutcomeEnum.DRAINING)));

    // when
    final ResumeProcessDefinitionResponse response =
        client
            .newResumeProcessDefinitionCommand()
            .processDefinitionId(PROCESS_DEFINITION_ID)
            .send()
            .join();

    // then
    final ProcessDefinitionResumptionInstruction request =
        gatewayService.getLastRequest(ProcessDefinitionResumptionInstruction.class);
    assertThat(request.getProcessDefinitionId()).isEqualTo(PROCESS_DEFINITION_ID);
    assertThat(request.getProcessDefinitionVersion()).isNull();
    assertThat(request.getProcessDefinitionKey()).isNull();
    assertThat(request.getTenantId()).isEqualTo(client.getConfiguration().getDefaultTenantId());
    assertThat(response.getProcessDefinitions())
        .extracting(r -> r.getOutcome())
        .containsExactly(Outcome.RESUMED, Outcome.DRAINING);
  }

  @Test
  void shouldResumeByIdAndVersionWithTenantAndIncludeProcessInstances() {
    // given
    gatewayService.onResumeProcessDefinitionsRequest(
        new ProcessDefinitionResumptionResult().batchOperationKey("batch-2"));

    // when
    final ResumeProcessDefinitionResponse response =
        client
            .newResumeProcessDefinitionCommand()
            .processDefinitionId(PROCESS_DEFINITION_ID)
            .version(2)
            .tenantId("tenant-a")
            .includeProcessInstances(true)
            .send()
            .join();

    // then
    final ProcessDefinitionResumptionInstruction request =
        gatewayService.getLastRequest(ProcessDefinitionResumptionInstruction.class);
    assertThat(request.getProcessDefinitionId()).isEqualTo(PROCESS_DEFINITION_ID);
    assertThat(request.getProcessDefinitionVersion()).isEqualTo(2);
    assertThat(request.getTenantId()).isEqualTo("tenant-a");
    assertThat(request.getIncludeProcessInstances()).isTrue();
    assertThat(response.getBatchOperationKey()).isEqualTo("batch-2");
  }

  @Test
  void shouldHandleMissingProcessDefinitionsInResponse() {
    // given
    gatewayService.onResumeProcessDefinitionsRequest(
        new ProcessDefinitionResumptionResult().processDefinitions(null));

    // when
    final ResumeProcessDefinitionResponse response =
        client
            .newResumeProcessDefinitionCommand()
            .processDefinitionKey(PROCESS_DEFINITION_KEY)
            .send()
            .join();

    // then
    assertThat(response.getProcessDefinitions()).isEmpty();
  }

  @Test
  void shouldMapUnknownOutcomeToUnknownEnumValue() {
    // given
    gatewayService.onResumeProcessDefinitionsRequest(
        new ProcessDefinitionResumptionResult()
            .addProcessDefinitionsItem(
                new ProcessDefinitionResumptionVersionResult()
                    .processDefinitionKey("1")
                    .processDefinitionVersion(1)
                    .outcome(OutcomeEnum.UNKNOWN_DEFAULT_OPEN_API)));

    // when
    final ResumeProcessDefinitionResponse response =
        client.newResumeProcessDefinitionCommand().processDefinitionKey(1L).send().join();

    // then
    assertThat(response.getProcessDefinitions().get(0).getOutcome())
        .isEqualTo(Outcome.UNKNOWN_ENUM_VALUE);
  }

  @Test
  void shouldRejectNullOrEmptyProcessDefinitionId() {
    assertThatThrownBy(() -> client.newResumeProcessDefinitionCommand().processDefinitionId(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionId");
    assertThatThrownBy(() -> client.newResumeProcessDefinitionCommand().processDefinitionId(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processDefinitionId");
  }

  @Test
  void shouldRejectNonPositiveVersion() {
    assertThatThrownBy(
            () ->
                client
                    .newResumeProcessDefinitionCommand()
                    .processDefinitionId(PROCESS_DEFINITION_ID)
                    .version(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }

  @Test
  void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        RestGatewayPaths.getResumeProcessDefinitionsUrl(),
        () -> new ProblemDetail().title("Draining definition").status(409));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newResumeProcessDefinitionCommand()
                    .processDefinitionKey(PROCESS_DEFINITION_KEY)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Draining definition");
  }
}
