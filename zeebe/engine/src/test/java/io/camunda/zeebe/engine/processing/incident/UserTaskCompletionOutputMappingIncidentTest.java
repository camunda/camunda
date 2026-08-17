/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class UserTaskCompletionOutputMappingIncidentTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setUserTaskCompletionVariableAuditEnabled(true));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldPreserveCompletingUserWhenOutputMappingIsRetried() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .userTask(
                    "task",
                    task -> task.zeebeUserTask().zeebeOutput("=assert(foo, foo != null)", "bar"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // when
    final long userTaskKey =
        ENGINE.userTask().ofInstance(processInstanceKey).complete("completing-user").getKey();
    final long incidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    ENGINE.variables().ofScope(processInstanceKey).withDocument("{'foo':'value'}").update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentKey).resolve("resolver-user");

    // then
    final var variableRecord =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("bar")
            .getFirst();
    assertThat(variableRecord.getAuthorizations())
        .containsEntry(AUTHORIZED_USERNAME, "completing-user");
    assertThat(variableRecord.getValue().getSource().getUserTaskKey()).isEqualTo(userTaskKey);
  }
}
