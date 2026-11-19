/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import io.camunda.zeebe.engine.util.RecordSizeBaseline;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Set;
import org.junit.Test;

/** Checks the log bytes of a batch operation. See {@link RecordSizeBaseline}. */
public final class BatchOperationRecordSizeBaselineTest extends AbstractBatchOperationTest {

  @Test
  public void shouldNotChangeRecordSizesOfCancelBatchOperation() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(DEFAULT_JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId("process").create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    final var claims = RecordSizeBaseline.oidcClaims(DEFAULT_USER.getUsername());
    final var startPositions = RecordSizeBaseline.lastPositions(engine);

    // when
    final long batchOperationKey =
        createNewCancelProcessInstanceBatchOperation(
            Set.of(processInstanceKey), claims.toClaimsMap(), claims);
    RecordingExporter.batchOperationLifecycleRecords(BatchOperationIntent.COMPLETED)
        .withBatchOperationKey(batchOperationKey)
        .await();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    RecordSizeBaseline.assertMatches(engine, "cancel-batch-operation", startPositions);
  }
}
