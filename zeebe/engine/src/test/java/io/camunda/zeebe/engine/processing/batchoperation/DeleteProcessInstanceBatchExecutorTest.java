/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class DeleteProcessInstanceBatchExecutorTest extends AbstractBatchOperationTest {

  @Test
  public void shouldDeleteProcessInstance() {

    // given
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, "admin");
    // create a simple process that completes immediately
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process").startEvent().endEvent("end_event_id").done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId("process").create();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("end_event_id")
                .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .getFirst())
        .isNotNull();

    // when
    final var batchOperationKey =
        createDeleteProcessInstanceBatchOperation(List.of(processInstanceKey), claims);

    assertThat(
            RecordingExporter.historyDeletionRecords()
                .withResourceKey(processInstanceKey)
                .limit(r -> r.getIntent() == HistoryDeletionIntent.DELETED))
        .extracting(Record::getIntent)
        .containsSequence(HistoryDeletionIntent.DELETE, HistoryDeletionIntent.DELETED);

    // then we have completed event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);
  }
}
