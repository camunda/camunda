/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue;
import io.camunda.zeebe.protocol.record.value.CaseStageStatus;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.File;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CasePocTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldWriteCaseEventsWithStageGraph() {
    // given
    final BpmnModelInstance model =
        Bpmn.readModelFromFile(new File("../../poc/case-search/case-poc.bpmn"));
    ENGINE.deployment().withXmlResource(model).deploy();

    // when
    final long pik =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("case-poc")
            .withVariables(Map.of("caseTitle", "Broken washing machine", "escalate", false))
            .create();

    completeTask(pik, "Task_Intake", 0, Map.of());
    completeTask(pik, "Task_Review", 0, Map.of("approved", false));
    completeTask(pik, "Task_Intake", 1, Map.of("escalate", true));
    completeTask(pik, "Task_EscalationA", 0, Map.of());
    completeTask(pik, "Task_EscalationB", 0, Map.of());
    awaitTask(pik, "Task_Resolution", 0);
    ENGINE.signal().withSignalName("withdraw").broadcast();
    completeTask(pik, "Task_Withdrawal", 0, Map.of());

    // then
    final List<Record<CaseRecordValue>> records =
        RecordingExporter.caseRecords()
            .withProcessInstanceKey(pik)
            .limit(r -> r.getIntent() == CaseIntent.COMPLETED)
            .toList();

    final var created = records.getFirst();
    assertThat(created.getIntent()).isEqualTo(CaseIntent.CREATED);
    assertThat(created.getKey()).isEqualTo(pik);
    assertThat(created.getValue().getTitle()).isEqualTo("Broken washing machine");
    assertThat(created.getValue().getBpmnProcessId()).isEqualTo("case-poc");
    assertThat(created.getValue().getStartElementIds()).containsExactly("Stage_Intake");
    assertThat(created.getValue().getStages())
        .extracting(
            s -> s.getElementId(),
            s -> s.getName(),
            s -> s.isAlternative(),
            s -> s.getNextElementIds())
        // stages are ordered by BFS from the start, not by document order
        .containsExactlyInAnyOrder(
            tuple("Stage_Intake", "Intake", false, List.of("Stage_Review", "Stage_EscalationA")),
            tuple("Stage_Review", "Review", false, List.of("Stage_Resolution", "Stage_Intake")),
            tuple("Stage_EscalationA", "Escalation A", true, List.of("Stage_EscalationB")),
            tuple("Stage_EscalationB", "Escalation B", true, List.of("Stage_Resolution")),
            tuple("Stage_Resolution", "Resolution", false, List.of()),
            tuple("Stage_Withdrawal", "Withdrawal", true, List.of()));

    assertThat(records.stream().filter(r -> r.getIntent() == CaseIntent.STAGE_UPDATED))
        .extracting(
            r -> r.getValue().getStages().getFirst().getElementId(),
            r -> r.getValue().getStages().getFirst().getStatus())
        .containsExactly(
            tuple("Stage_Intake", CaseStageStatus.ACTIVE),
            tuple("Stage_Intake", CaseStageStatus.COMPLETED),
            tuple("Stage_Review", CaseStageStatus.ACTIVE),
            tuple("Stage_Review", CaseStageStatus.COMPLETED),
            tuple("Stage_Intake", CaseStageStatus.ACTIVE),
            tuple("Stage_Intake", CaseStageStatus.COMPLETED),
            tuple("Stage_EscalationA", CaseStageStatus.ACTIVE),
            tuple("Stage_EscalationA", CaseStageStatus.COMPLETED),
            tuple("Stage_EscalationB", CaseStageStatus.ACTIVE),
            tuple("Stage_EscalationB", CaseStageStatus.COMPLETED),
            tuple("Stage_Resolution", CaseStageStatus.ACTIVE),
            tuple("Stage_Resolution", CaseStageStatus.TERMINATED),
            tuple("Stage_Withdrawal", CaseStageStatus.ACTIVE),
            tuple("Stage_Withdrawal", CaseStageStatus.COMPLETED));

    assertThat(records.getLast().getIntent()).isEqualTo(CaseIntent.COMPLETED);
  }

  private static long awaitTask(final long pik, final String elementId, final int skip) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(pik)
        .withElementId(elementId)
        .skip(skip)
        .getFirst()
        .getKey();
  }

  private static void completeTask(
      final long pik, final String elementId, final int skip, final Map<String, Object> variables) {
    ENGINE
        .userTask()
        .ofInstance(pik)
        .withKey(awaitTask(pik, elementId, skip))
        .withVariables(variables)
        .complete();
  }
}
