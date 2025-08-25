/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ClusterVariableTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void createClusterVariable() {
    final var record = ENGINE_RULE.variable().withClusterVariable("KEY_1", "VALUE").create();
    Assertions.assertThat(record).hasIntent(VariableIntent.CREATED).hasRecordType(RecordType.EVENT);
  }

  @Test
  public void createClusterVariableAlreadyExists() {
    ENGINE_RULE.variable().withClusterVariable("KEY_2", "VALUE").create();
    final var record =
        ENGINE_RULE.variable().withClusterVariable("KEY_2", "VALUE_2").expectRejection().create();

    Assertions.assertThat(record)
        .hasIntent(VariableIntent.CREATE)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason("This variable already exists");
  }

  @Test
  public void updateClusterVariable() {
    final var creation = ENGINE_RULE.variable().withClusterVariable("KEY_3", "VALUE").create();
    final var record =
        ENGINE_RULE.variable().withKey(creation.getKey()).withValue("VALUE_2").update();

    Assertions.assertThat(record).hasIntent(VariableIntent.UPDATED).hasRecordType(RecordType.EVENT);
  }

  @Test
  public void updateClusterVariableThatDoesNotExist() {
    final var record =
        ENGINE_RULE.variable().withKey(123456789L).expectRejection().withValue("VALUE_2").update();

    Assertions.assertThat(record)
        .hasIntent(VariableIntent.UPDATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason("This variable does not exists and thus can not be updated");
  }

  @Test
  public void deleteClusterVariable() {
    final var creation = ENGINE_RULE.variable().withClusterVariable("KEY_4", "VALUE").create();
    final var record = ENGINE_RULE.variable().withKey(creation.getKey()).delete();

    Assertions.assertThat(record).hasIntent(VariableIntent.DELETED).hasRecordType(RecordType.EVENT);
  }

  @Test
  public void deleteClusterVariableThatDoesNotExist() {
    final var record = ENGINE_RULE.variable().withKey(123456789L).expectRejection().delete();

    Assertions.assertThat(record)
        .hasIntent(VariableIntent.DELETE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason("This variable does not exists and thus can not be deleted");
  }

  @Test
  public void createCLusterVariableWithInvalidName() {
    final var record =
        ENGINE_RULE.variable().expectRejection().withClusterVariable("KEY 2", "VALUE").create();

    Assertions.assertThat(record)
        .hasIntent(VariableIntent.CREATE)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Invalid Camunda variable name: 'KEY 2'. The name must not start with a digit, contain whitespace, or use any of the following characters: +-*/=><?.. Additionally, variable names cannot be any of the reserved keywords or literals: [null, true, false, function, if, then, else, for, return, between, instance, of, not, in, and, or, some, every, satisfies].");
  }
}
