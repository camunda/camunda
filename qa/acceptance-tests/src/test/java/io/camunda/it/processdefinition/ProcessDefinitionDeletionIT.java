/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.processdefinition;

import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessDefinitionState;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class ProcessDefinitionDeletionIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldMarkProcessDefinitionAsDeletedWhenDeleted() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var process =
        deployProcessAndWaitForIt(
            camundaClient,
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId + ".bpmn");
    final var processDefinitionKey = process.getProcessDefinitionKey();

    // when
    camundaClient.newDeleteResourceCommand(processDefinitionKey).send().join();

    // then - the process definition is marked as deleted rather than removed
    Awaitility.await("Process definition should be marked as deleted")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var deleted =
                  camundaClient
                      .newProcessDefinitionSearchRequest()
                      .filter(
                          f ->
                              f.processDefinitionKey(processDefinitionKey)
                                  .state(ProcessDefinitionState.DELETED))
                      .send()
                      .join()
                      .items();
              assertThat(deleted).hasSize(1);
              assertThat(deleted.getFirst().getState()).isEqualTo(ProcessDefinitionState.DELETED);
            });

    // and - searching with state=ACTIVE should not return the deleted definition
    final var nonDeleted =
        camundaClient
            .newProcessDefinitionSearchRequest()
            .filter(
                f ->
                    f.processDefinitionKey(processDefinitionKey)
                        .state(ProcessDefinitionState.ACTIVE))
            .send()
            .join()
            .items();
    assertThat(nonDeleted).isEmpty();
  }
}
