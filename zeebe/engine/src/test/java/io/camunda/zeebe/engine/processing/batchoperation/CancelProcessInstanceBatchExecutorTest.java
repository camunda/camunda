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
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public final class CancelProcessInstanceBatchExecutorTest extends AbstractBatchOperationTest {

  private static final String JOB_TYPE = "test";

  @Test
  public void shouldCancelProcessInstance() {
    // given
    final var user = createUser();
    addProcessDefinitionPermissionsToUser(user, PermissionType.CANCEL_PROCESS_INSTANCE);
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, user.getUsername());

    // create a process with a failed job
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask(
                    "failingTask", t -> t.zeebeJobType(JOB_TYPE).zeebeInputExpression("foo", "foo"))
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("process")
            .withVariables(Maps.of(entry("foo", "bar")))
            .create();

    final var batchOperationKey =
        createNewCancelProcessInstanceBatchOperation(Set.of(processInstanceKey), claims);

    // then we have completed event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withIntents(ProcessInstanceIntent.CANCEL, ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withRecordKey(processInstanceKey)
                .limit(
                    r ->
                        r.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATED
                            && r.getValue().getBpmnElementType() == BpmnElementType.PROCESS))
        .extracting(Record::getIntent)
        .containsSequence(ProcessInstanceIntent.CANCEL, ProcessInstanceIntent.ELEMENT_TERMINATED);
  }

  @Test
  public void shouldRejectNonExistingProcessInstance() {
    // given
    final Map<String, Object> claims = Map.of("claim1", "value1", "claim2", "value2");

    // some random keys
    final var processInstanceKey = 42L;

    final var batchOperationKey =
        createNewCancelProcessInstanceBatchOperation(Set.of(processInstanceKey), claims);

    // then we have completed event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and we have a rejected incident resolve command
    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyCommandRejections()
                .withRecordKey(processInstanceKey)
                .withIntents(ProcessInstanceIntent.CANCEL)
                .getFirst())
        .satisfies(
            r -> {
              assertThat(r.getBatchOperationReference()).isEqualTo(batchOperationKey);
              assertThat(r.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
            });
  }
}
