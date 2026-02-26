/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.db.rdbms.write.RdbmsWriterConfig;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@MultiDbTest
public class ProcessDefinitionCreateIT {

  private static CamundaClient camundaClient;

  @Test
  @EnabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
  void shouldRejectProcessDefinitionOnRdbmsWhenIdIsTooLong() {
    final var processDefinition =
        Bpmn.createExecutableProcess(
                "p".repeat(RdbmsWriterConfig.DEFAULT_MAX_VARCHAR_FIELD_LENGTH + 1))
            .name("my process")
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
            "ERROR: IDs must not be longer than the configured max-id-length of %d characters"
                .formatted(RdbmsWriterConfig.DEFAULT_MAX_VARCHAR_FIELD_LENGTH));
  }
}
