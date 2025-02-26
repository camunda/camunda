/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

public class ContinuouslyReplayTest {

  private final ListLogStorage sharedStorage = new ListLogStorage();

  @Rule
  public final EngineRule replay =
      EngineRule.withSharedStorage(sharedStorage)
          .withStreamProcessorMode(StreamProcessorMode.REPLAY);

  @Rule public final EngineRule processing = EngineRule.withSharedStorage(sharedStorage);

  @Test
  public void shouldBuildTheSameStateOnProcessingAndReplay() {
    // given
    processing
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
        .deploy();

    // when
    final var processInstanceKey = processing.processInstance().ofBpmnProcessId("process").create();

    // then
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    assertStates();
  }

  private void assertStates() {
    Awaitility.await("await that the replay state is equal to the processing state")
        .untilAsserted(
            () -> {
              final var replayState = replay.collectState();
              final var processingState = processing.collectState();

              final var softly = new SoftAssertions();

              processingState.entrySet().stream()
                  // ignores transient states
                  // this will happen anyway then on leader replay
                  .filter(entry -> entry.getKey() != ZbColumnFamilies.DEFAULT)
                  // Migrations ColumnFamily is not filled using events, but on broker start
                  .filter(entry -> entry.getKey() != ZbColumnFamilies.MIGRATIONS_STATE)
                  .forEach(
                      entry -> {
                        final var column = entry.getKey();
                        final var processingEntries = entry.getValue();
                        final var replayEntries = replayState.get(column);

                        if (processingEntries.isEmpty()) {
                          softly
                              .assertThat(replayEntries)
                              .describedAs(
                                  "The state column '%s' should be empty after replay", column)
                              .isEmpty();
                        } else {
                          softly
                              .assertThat(replayEntries)
                              .describedAs(
                                  "The state column '%s' has different entries after replay",
                                  column)
                              .containsExactlyInAnyOrderEntriesOf(processingEntries);
                        }
                      });

              softly.assertAll();
            });
  }
}
