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
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public final class SuspendProcessInstanceBatchExecutorTest extends AbstractBatchOperationTest {

  @Test
  public void shouldDispatchSuspendCommandForEachItem() {
    // given
    final var user = createUser();
    addProcessDefinitionPermissionsToUser(user, PermissionType.SUSPEND_PROCESS_INSTANCE);
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, user.getUsername());

    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .deploy();

    final var processInstanceKey = engine.processInstance().ofBpmnProcessId("process").create();

    // when
    final var batchOperationKey =
        createNewSuspendProcessInstanceBatchOperation(Set.of(processInstanceKey), claims);

    // then
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    final var suspendCommand =
        RecordingExporter.processInstanceRecords()
            .withRecordType(RecordType.COMMAND)
            .withIntents(ProcessInstanceIntent.SUSPEND)
            .withRecordKey(processInstanceKey)
            .getFirst();
    assertThat(suspendCommand.getAuthorizations()).isEqualTo(claims);
    assertThat(suspendCommand.getBatchOperationReference()).isEqualTo(batchOperationKey);
  }
}
