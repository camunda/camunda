/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import static io.zeebe.engine.util.StreamProcessingComposite.getLogName;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETING;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.util.ListLogStorage;
import io.zeebe.engine.util.RecordStream;
import io.zeebe.engine.util.StreamProcessingComposite;
import io.zeebe.engine.util.TestStreams;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.CloseHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class StreamProcessorInconsistentPositionTest {

  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final AutoCloseableRule closeables = new AutoCloseableRule();
  private final ControlledActorClock clock = new ControlledActorClock();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(clock);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(closeables);

  private StreamProcessingComposite firstStreamProcessorComposite;
  private StreamProcessingComposite secondStreamProcessorComposite;
  private TestStreams testStreams;

  @Before
  public void setup() {

    testStreams = new TestStreams(tempFolder, closeables, actorSchedulerRule.get());

    final var listLogStorage = new ListLogStorage();
    testStreams.createLogStream(getLogName(1), 1, listLogStorage);
    testStreams.createLogStream(getLogName(2), 2, listLogStorage);

    firstStreamProcessorComposite =
        new StreamProcessingComposite(testStreams, 1, DefaultZeebeDbFactory.defaultFactory());
    secondStreamProcessorComposite =
        new StreamProcessingComposite(testStreams, 2, DefaultZeebeDbFactory.defaultFactory());
  }

  @After
  public void tearDown() {
    // we expect that AsyncSnapshotDirector can't be closed without problems
    CloseHelper.quietClose(() -> testStreams.closeProcessor(getLogName(1)));
  }

  @Test
  public void shouldNotStartOnInconsistentLog() {
    // given
    final var position =
        firstStreamProcessorComposite.writeWorkflowInstanceEvent(ELEMENT_ACTIVATING);
    final var secondPosition =
        firstStreamProcessorComposite.writeWorkflowInstanceEvent(ELEMENT_ACTIVATED);
    waitUntil(
        () ->
            new RecordStream(testStreams.events(getLogName(1)))
                .onlyWorkflowInstanceRecords()
                .withIntent(ELEMENT_ACTIVATED)
                .exists());

    final var otherPosition =
        secondStreamProcessorComposite.writeWorkflowInstanceEvent(ELEMENT_COMPLETING);
    final var otherSecondPosition =
        secondStreamProcessorComposite.writeWorkflowInstanceEvent(ELEMENT_COMPLETED);
    waitUntil(
        () ->
            new RecordStream(testStreams.events(getLogName(2)))
                .onlyWorkflowInstanceRecords()
                .withIntent(ELEMENT_COMPLETED)
                .exists());

    assertThat(position).isEqualTo(otherPosition);
    assertThat(secondPosition).isEqualTo(otherSecondPosition);

    // when
    final TypedRecordProcessor typedRecordProcessor = mock(TypedRecordProcessor.class);
    final var streamProcessor =
        firstStreamProcessorComposite.startTypedStreamProcessor(
            (processors, context) ->
                processors
                    .onEvent(ValueType.WORKFLOW_INSTANCE, ELEMENT_ACTIVATING, typedRecordProcessor)
                    .onEvent(ValueType.WORKFLOW_INSTANCE, ELEMENT_ACTIVATED, typedRecordProcessor));

    // then
    waitUntil(streamProcessor::isFailed);
    assertThat(streamProcessor.isFailed()).isTrue();
  }
}
