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

public class CreateGlobalVariableTest {

  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void createGlobalVariable() {
    final var record =
        ENGINE_RULE.variable().withGlobalVariable("KEY_1", "VALUE").expectCreated().create();
    Assertions.assertThat(record).hasIntent(VariableIntent.CREATED).hasRecordType(RecordType.EVENT);
  }

  @Test
  public void globalVariableAlreadyExists() {
    ENGINE_RULE.variable().withGlobalVariable("KEY_2", "VALUE").create();
    final var record =
        ENGINE_RULE.variable().withGlobalVariable("KEY_2", "VALUE_2").expectRejection().create();

    Assertions.assertThat(record)
        .hasIntent(VariableIntent.CREATE)
        .hasRejectionType(RejectionType.ALREADY_EXISTS)
        .hasRejectionReason("This variable already exists");
  }
}
