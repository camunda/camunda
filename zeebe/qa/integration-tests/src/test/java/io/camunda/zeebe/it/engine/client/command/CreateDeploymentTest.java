/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

@ZeebeIntegration
public final class CreateDeploymentTest {

  CamundaClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withBrokerConfig(b -> b.getNetwork().setMaxMessageSize(DataSize.ofMegabytes(1)));

  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldDeployProcessModel(final boolean useRest, final TestInfo testInfo) {
    // given
    final String processId = "process-" + testInfo.getTestMethod().get().getName();
    final String resourceName = processId + ".bpmn";

    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test"))
            .endEvent()
            .done();

    // when
    final DeploymentEvent result =
        getCommand(client, useRest).addProcessModel(process, resourceName).send().join();

    // then
    assertThat(result.getKey()).isGreaterThan(0);
    assertThat(result.getProcesses()).hasSize(1);

    final Process deployedProcess = result.getProcesses().get(0);
    assertThat(deployedProcess.getBpmnProcessId()).isEqualTo(processId);
    assertThat(deployedProcess.getVersion()).isEqualTo(1);
    assertThat(deployedProcess.getProcessDefinitionKey()).isGreaterThan(0);
    assertThat(deployedProcess.getResourceName()).isEqualTo(resourceName);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldDeployDecisionModel(final boolean useRest) {
    // given
    final String resourceName = "dmn/drg-force-user.dmn";

    // when
    final DeploymentEvent result =
        getCommand(client, useRest).addResourceFromClasspath(resourceName).send().join();

    // then
    assertThat(result.getKey()).isPositive();
    assertThat(result.getDecisionRequirements()).hasSize(1);
    assertThat(result.getDecisions()).hasSize(2);

    final var decisionRequirements = result.getDecisionRequirements().get(0);
    assertThat(decisionRequirements.getDmnDecisionRequirementsId()).isEqualTo("force_users");
    assertThat(decisionRequirements.getDmnDecisionRequirementsName()).isEqualTo("Force Users");
    assertThat(decisionRequirements.getVersion()).isEqualTo(1);
    assertThat(decisionRequirements.getDecisionRequirementsKey()).isPositive();
    assertThat(decisionRequirements.getResourceName()).isEqualTo(resourceName);

    final var decision1 = result.getDecisions().get(0);
    assertThat(decision1.getDmnDecisionId()).isEqualTo("jedi_or_sith");
    assertThat(decision1.getDmnDecisionName()).isEqualTo("Jedi or Sith");
    assertThat(decision1.getVersion()).isEqualTo(1);
    assertThat(decision1.getDecisionKey()).isPositive();
    assertThat(decision1.getDmnDecisionRequirementsId()).isEqualTo("force_users");
    assertThat(decision1.getDecisionRequirementsKey()).isPositive();

    final var decision2 = result.getDecisions().get(1);
    assertThat(decision2.getDmnDecisionId()).isEqualTo("force_user");
    assertThat(decision2.getDmnDecisionName()).isEqualTo("Which force user?");
    assertThat(decision2.getVersion()).isEqualTo(1);
    assertThat(decision2.getDecisionKey()).isPositive();
    assertThat(decision2.getDmnDecisionRequirementsId()).isEqualTo("force_users");
    assertThat(decision2.getDecisionRequirementsKey()).isPositive();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectDeployIfProcessIsInvalid(final boolean useRest) {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process").startEvent().serviceTask("task").done();

    // when
    final var command = getCommand(client, useRest).addProcessModel(process, "process.bpmn").send();

    // then
    assertThatThrownBy(command::join)
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Must have exactly one 'zeebe:taskDefinition' extension element");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectDeployIfResourceIsTooLarge(final boolean useRest) {
    // when
    final var modelThatFitsJustWithinMaxMessageSize =
        Bpmn.createExecutableProcess("PROCESS")
            .startEvent()
            .documentation("x".repeat(1046700))
            .done();
    final var command =
        getCommand(client, useRest)
            .addProcessModel(modelThatFitsJustWithinMaxMessageSize, "too_large_process.bpmn")
            .send();

    // then
    assertThatThrownBy(command::join)
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("rejected with code 'EXCEEDED_BATCH_RECORD_SIZE'");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldNotWriteResourcesInformationInRejectedRecords() {
    // when
    final var modelThatFitsJustWithinMaxMessageSize =
        Bpmn.createExecutableProcess("PROCESS")
            .startEvent()
            .documentation("x".repeat((1046900)))
            .done();
    final var command =
        getCommand(client, false)
            .addProcessModel(modelThatFitsJustWithinMaxMessageSize, "too_large_process.bpmn")
            .send();

    // then
    final var rejectedRecords =
        RecordingExporter.records()
            .filter(record -> RecordType.COMMAND_REJECTION.equals(record.getRecordType()))
            .toList();

    rejectedRecords.stream()
        .map(Record::getValue)
        .forEach(
            recordValue -> {
              if (recordValue instanceof DeploymentRecord) {
                assertThat(((DeploymentRecord) recordValue).getResources()).isEmpty();
              }
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldDeployForm(final boolean useRest) {
    // given
    final String resourceName = "form/test-form-1.form";

    // when
    final DeploymentEvent result =
        getCommand(client, useRest).addResourceFromClasspath(resourceName).send().join();

    // then
    assertThat(result.getKey()).isPositive();
    assertThat(result.getForm()).hasSize(1);

    final var form = result.getForm().get(0);
    assertThat(form.getFormId()).isEqualTo("Form_0w7r08e");
    assertThat(form.getResourceName()).isEqualTo(resourceName);
    assertThat(form.getVersion()).isEqualTo(1);
    assertThat(form.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(form.getFormKey()).isPositive();
  }

  private DeployResourceCommandStep1 getCommand(final CamundaClient client, final boolean useRest) {
    final DeployResourceCommandStep1 deployResourceCommand = client.newDeployResourceCommand();
    return useRest ? deployResourceCommand.useRest() : deployResourceCommand.useGrpc();
  }
}
