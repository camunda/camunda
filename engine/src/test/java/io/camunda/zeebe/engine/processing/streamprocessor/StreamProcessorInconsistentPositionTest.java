/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static io.camunda.zeebe.engine.util.StreamProcessingComposite.getLogName;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ACTIVATE_ELEMENT;
import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.util.RecordStream;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.StreamProcessingComposite;
import io.camunda.zeebe.engine.util.TestStreams;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import org.agrona.CloseHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

public final class StreamProcessorInconsistentPositionTest {

  private static final ProcessInstanceRecord PROCESS_INSTANCE_RECORD = Records.processInstance(1);

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
        new StreamProcessingComposite(
            testStreams, 1, DefaultZeebeDbFactory.defaultFactory(), actorSchedulerRule.get());
    secondStreamProcessorComposite =
        new StreamProcessingComposite(
            testStreams, 2, DefaultZeebeDbFactory.defaultFactory(), actorSchedulerRule.get());
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
        firstStreamProcessorComposite.writeCommand(
            ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    final var secondPosition =
        firstStreamProcessorComposite.writeCommand(
            ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    waitUntil(
        () ->
            new RecordStream(testStreams.events(getLogName(1)))
                    .onlyProcessInstanceRecords()
                    .withIntent(ACTIVATE_ELEMENT)
                    .count()
                == 2);

    final var otherPosition =
        secondStreamProcessorComposite.writeCommand(
            ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    final var otherSecondPosition =
        secondStreamProcessorComposite.writeCommand(
            ProcessInstanceIntent.ACTIVATE_ELEMENT, PROCESS_INSTANCE_RECORD);
    waitUntil(
        () ->
            new RecordStream(testStreams.events(getLogName(2)))
                    .onlyProcessInstanceRecords()
                    .withIntent(ACTIVATE_ELEMENT)
                    .count()
                == 4);

    assertThat(position).isEqualTo(otherPosition);
    assertThat(secondPosition).isEqualTo(otherSecondPosition);

    // when
    final var typedRecordProcessor = mock(TypedRecordProcessor.class);
    final var streamProcessor =
        firstStreamProcessorComposite.startTypedStreamProcessorNotAwaitOpening(
            (processors, context) ->
                processors.onCommand(
                    ValueType.PROCESS_INSTANCE, ACTIVATE_ELEMENT, typedRecordProcessor));

    // then
    waitUntil(streamProcessor::isFailed);
    assertThat(streamProcessor.isFailed()).isTrue();
  }
}
