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
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class MigrateProcessesBatchExecutorTest extends AbstractBatchOperationTest {

  @Test
  public void shouldMigrateProcess() {
    // given
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, "admin");

    // create a process with a user task a
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process").startEvent().userTask("userTaskA").done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();

    // create another process with a user task b
    final long processDefinitionKey2 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process2").startEvent().userTask("userTaskB").done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();

    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("process")
            .withVariables(Maps.of(entry("foo", "bar")))
            .create();

    // wait for the user task to exist
    RecordingExporter.jobRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withIntent(JobIntent.CREATED)
        .getFirst();

    // then start the batch where we migrate to a target process definition with a user task b
    final var batchOperationKey =
        createNewMigrateProcessesBatchOperation(
            List.of(processInstanceKey),
            processDefinitionKey2,
            Map.of("userTaskA", "userTaskB"),
            claims);

    // then we have completed event
    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and we have migrate commands
    final var migrationCommand =
        RecordingExporter.processInstanceMigrationRecords()
            .withRecordType(RecordType.COMMAND)
            .withRecordKey(processInstanceKey)
            .getFirst();
    assertThat(migrationCommand.getIntent()).isEqualTo(ProcessInstanceMigrationIntent.MIGRATE);
    assertThat(migrationCommand.getAuthorizations()).isEqualTo(claims);
    assertThat(migrationCommand.getBatchOperationReference()).isEqualTo(batchOperationKey);
  }

  @Test
  public void shouldHandleRejectedMigrateProcessCommand() {
    // given
    final var user = createUser();
    addProcessDefinitionPermissionsToUser(user, PermissionType.UPDATE_PROCESS_INSTANCE);
    final Map<String, Object> claims = Map.of(AUTHORIZED_USERNAME, user.getUsername());

    // create a process with a user task a
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .exclusiveGateway()
                .conditionExpression("canBeMigrated")
                .userTask("userTaskA")
                .moveToLastExclusiveGateway()
                .defaultFlow()
                .userTask("willNotBeMigrated")
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();

    // create another process with a user task b
    final long processDefinitionKey2 =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess("process2").startEvent().userTask("userTaskB").done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst()
            .getProcessDefinitionKey();

    final var processInstanceKey1 =
        engine
            .processInstance()
            .ofBpmnProcessId("process")
            .withVariables(Maps.of(entry("canBeMigrated", true)))
            .create();

    final var processInstanceKey2 =
        engine
            .processInstance()
            .ofBpmnProcessId("process")
            .withVariables(Maps.of(entry("canBeMigrated", false)))
            .create();

    final var processInstanceKey3 =
        engine
            .processInstance()
            .ofBpmnProcessId("process")
            .withVariables(Maps.of(entry("canBeMigrated", true)))
            .create();

    // wait for the user task to exist
    RecordingExporter.jobRecords()
        .withProcessInstanceKey(processInstanceKey1)
        .withIntent(JobIntent.CREATED)
        .getFirst();

    RecordingExporter.jobRecords()
        .withProcessInstanceKey(processInstanceKey2)
        .withIntent(JobIntent.CREATED)
        .getFirst();

    RecordingExporter.jobRecords()
        .withProcessInstanceKey(processInstanceKey3)
        .withIntent(JobIntent.CREATED)
        .getFirst();

    // then start the batch where we give a migration plan that allows to migrate only process
    // instances 1 and 3 but not 2
    final var batchOperationKey =
        createNewMigrateProcessesBatchOperation(
            List.of(processInstanceKey1, processInstanceKey2, processInstanceKey3),
            processDefinitionKey2,
            Map.of("userTaskA", "userTaskB"),
            claims);

    // then we have executed and completed event
    assertThat(
            RecordingExporter.batchOperationExecutionRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationExecutionIntent.EXECUTED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationExecutionIntent.EXECUTED);

    assertThat(
            RecordingExporter.batchOperationLifecycleRecords()
                .withBatchOperationKey(batchOperationKey)
                .onlyEvents()
                .limit(r -> r.getIntent() == BatchOperationIntent.COMPLETED))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.COMPLETED);

    // and we have a rejected command
    Assertions.assertThat(
            RecordingExporter.processInstanceMigrationRecords()
                .withRecordKey(processInstanceKey2)
                .onlyCommandRejections()
                .getFirst())
        .hasKey(processInstanceKey2)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasIntent(ProcessInstanceMigrationIntent.MIGRATE);
  }
}
