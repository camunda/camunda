/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Set;
import org.junit.Test;

public final class ResumeProcessInstanceBatchExecutorTest extends AbstractBatchOperationTest {

  @Test
  public void shouldDispatchResumeCommandForEachItem() {
    // given
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId("process").create();

    // when
    final var batchOperationKey =
        createNewResumeProcessInstanceBatchOperation(Set.of(processInstanceKey));

    // then
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntents(ProcessInstanceIntent.RESUME)
                .withRecordKey(processInstanceKey)
                .getFirst())
        .satisfies(r -> assertThat(r.getBatchOperationReference()).isEqualTo(batchOperationKey));
  }
}
