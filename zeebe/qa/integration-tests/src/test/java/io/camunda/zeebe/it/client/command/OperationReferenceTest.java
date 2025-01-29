/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class OperationReferenceTest {
  private static final long OPERATION_REFERENCE = 1234L;

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker zeebe;

  @AutoClose
  private final CamundaClient client =
      zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofMinutes(2)).build();

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    zeebe = new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();
  }

  @Test
  void shouldIncludeOperationReferenceInExportedCommandRecord() {
    // Given
    final ZeebeResourcesHelper helper = new ZeebeResourcesHelper(client);
    final var modelInstance = helper.createSingleJobModelInstance("test", c -> {});
    final long processDefinitionKey = helper.deployProcess(modelInstance);
    final long processInstanceKey = helper.createProcessInstance(processDefinitionKey);

    // When
    client
        .newCancelInstanceCommand(processInstanceKey)
        .operationReference(OPERATION_REFERENCE)
        .send()
        .join();

    // Then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
                .withRecordKey(processInstanceKey)
                .limit(1)
                .getFirst()
                .getOperationReference())
        .describedAs("Should contain client operationReference")
        .isEqualTo(OPERATION_REFERENCE);
  }

  @Test
  void shouldIncludeOperationReferenceInFollowUpRecords() {
    // Given
    final ZeebeResourcesHelper helper = new ZeebeResourcesHelper(client);
    final var modelInstance = helper.createSingleJobModelInstance("test", c -> {});
    final long processDefinitionKey = helper.deployProcess(modelInstance);
    final long processInstanceKey = helper.createProcessInstance(processDefinitionKey);

    // When
    client
        .newCancelInstanceCommand(processInstanceKey)
        .operationReference(OPERATION_REFERENCE)
        .send()
        .join();

    // Then
    final var cancelCommand =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
            .withRecordKey(processInstanceKey)
            .limit(1)
            .getFirst();

    final var followUpRecords =
        RecordingExporter.records()
            .withSourceRecordPosition(cancelCommand.getPosition())
            .limit(
                r ->
                    r.getKey() == processInstanceKey
                        && r.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATED);

    assertThat(followUpRecords)
        .hasSizeGreaterThan(0)
        .describedAs("Should contain client operationReference")
        .allMatch(r -> r.getOperationReference() == OPERATION_REFERENCE);
  }

  @Test
  void shouldHaveNegativeOneAsOperationReferenceIfNotSpecifiedInCommand() {
    // Given
    final ZeebeResourcesHelper helper = new ZeebeResourcesHelper(client);
    final var modelInstance = helper.createSingleJobModelInstance("test", c -> {});
    final long processDefinitionKey = helper.deployProcess(modelInstance);
    final long processInstanceKey = helper.createProcessInstance(processDefinitionKey);

    // When
    client.newCancelInstanceCommand(processInstanceKey).send().join();

    // Then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
                .withRecordKey(processInstanceKey)
                .limit(1)
                .getFirst()
                .getOperationReference())
        .describedAs("Should contain -1 operationReference")
        .isEqualTo(-1);
  }

  @Test
  void shouldAcceptNegativeOneAsOperationReference() {
    // Given
    final ZeebeResourcesHelper helper = new ZeebeResourcesHelper(client);
    final var modelInstance = helper.createSingleJobModelInstance("test", c -> {});
    final long processDefinitionKey = helper.deployProcess(modelInstance);
    final long processInstanceKey = helper.createProcessInstance(processDefinitionKey);

    // When
    client.newCancelInstanceCommand(processInstanceKey).operationReference(-1).send().join();

    // Then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.CANCEL)
                .withRecordKey(processInstanceKey)
                .limit(1)
                .getFirst()
                .getOperationReference())
        .describedAs("Should contain client operationReference")
        .isEqualTo(-1);
  }
}
