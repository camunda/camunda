/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@MultiDbTest
public class ProcessDefinitionCreateIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldDeployProcessDefinitionWithMultibyteUTF8() {
    final String name = "カムンダ";
    final var processDefinition =
        Bpmn.createExecutableProcess("processId").name(name).startEvent().endEvent().done();

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(processDefinition, "process.bpmn")
        .execute();

    waitForProcessesToBeDeployed(camundaClient, 1);

    final var result =
        camundaClient.newProcessDefinitionSearchRequest().filter(f -> f.name(name)).execute();
    assertThat(result).isNotNull();
    assertThat(result.items()).hasSize(1);
    assertThat(result.items()).extracting(ProcessDefinition::getName).containsExactly(name);
  }

  @Test
  @EnabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
  void shouldRejectProcessDefinitionOnRdbmsWhenNameIsTooLong() {
    final var processDefinition =
        Bpmn.createExecutableProcess("processId")
            .name("p".repeat(RdbmsWriterConfig.DEFAULT_MAX_VARCHAR_FIELD_LENGTH + 1))
            .startEvent()
            .endEvent()
            .done();

    assertThatThrownBy(
            () -> {
              camundaClient
                  .newDeployResourceCommand()
                  .addProcessModel(processDefinition, "process.bpmn")
                  .execute();
            })
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "ERROR: Names must not be longer than the configured max-name-length of %d characters"
                .formatted(RdbmsWriterConfig.DEFAULT_MAX_VARCHAR_FIELD_LENGTH));
  }

  @Test
  @EnabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
  void shouldRejectProcessDefinitionOnRdbmsWhenNameIsTooLongUtf8() {
    final var processDefinition =
        Bpmn.createExecutableProcess("processId")
            .name("ü".repeat(RdbmsWriterConfig.DEFAULT_MAX_VARCHAR_FIELD_LENGTH + 1))
            .startEvent()
            .endEvent()
            .done();

    assertThatThrownBy(
            () -> {
              camundaClient
                  .newDeployResourceCommand()
                  .addProcessModel(processDefinition, "process.bpmn")
                  .execute();
            })
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "ERROR: Names must not be longer than the configured max-name-length of %d characters"
                .formatted(RdbmsWriterConfig.DEFAULT_MAX_VARCHAR_FIELD_LENGTH));
  }

  @Test
  @EnabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
  void shouldDeployProcessDefinitionOnRdbmsWhenNameIsExactlyWithinSizeLimit() {
    final var processDefinition =
        Bpmn.createExecutableProcess("processId")
            .name("p".repeat(RdbmsWriterConfig.DEFAULT_MAX_VARCHAR_FIELD_LENGTH))
            .startEvent()
            .endEvent()
            .done();

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(processDefinition, "process.bpmn")
        .execute();

    waitForProcessesToBeDeployed(camundaClient, 1);
  }

  @Test
  @EnabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
  void shouldDeployProcessDefinitionOnRdbmsWhenNameIsExactlyWithinSizeLimitUtf8() {
    final var processDefinition =
        Bpmn.createExecutableProcess("processId")
            .name("ü".repeat(RdbmsWriterConfig.DEFAULT_MAX_VARCHAR_FIELD_LENGTH))
            .startEvent()
            .endEvent()
            .done();

    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(processDefinition, "process.bpmn")
        .execute();

    waitForProcessesToBeDeployed(camundaClient, 1);
  }
}
