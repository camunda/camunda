/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ProcessInstanceResumeRejectionCommandTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectResumeWhenProcessInstanceNotFound() {
    // when - try to resume a non-existing process instance
    final var rejectionRecord =
        ENGINE
            .processInstance()
            .withInstanceKey(-1)
            .onPartition(1)
            .expectResumeRejection()
            .resume();

    // then
    assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceIntent.RESUME)
        .hasRejectionType(RejectionType.NOT_FOUND);
  }
}
