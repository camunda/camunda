/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.UserTaskLookupResponses;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskLookupRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskLookupIntent;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.ByteValue;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * The lookup returns the task's variables in the response, so the response can grow past the max
 * message size. The engine here is configured with a small max message size, so a few KB of
 * variables are enough to exceed it. Kept apart from {@link UserTaskLookupTest} because that one
 * runs with the default limit.
 */
public final class UserTaskLookupSizeLimitTest {

  private static final int MAX_RESPONSE_MESSAGE_SIZE = 64 * 1024;

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setMaxResponseMessageSize(MAX_RESPONSE_MESSAGE_SIZE));

  private static final UserTaskLookupResponses RESPONSES = new UserTaskLookupResponses();
  private static final AtomicLong REQUEST_IDS = new AtomicLong();

  // together 72 KB, more than the 64 KB max message size; distinct sizes, so the order in the
  // rejection message is fixed
  private static final String LARGEST = kilobytes(32);
  private static final String LARGER = kilobytes(24);
  private static final String LARGE = kilobytes(16);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String processId = Strings.newRandomValidBpmnId();

  @BeforeClass
  public static void installResponseCapture() {
    RESPONSES.install(ENGINE.getCommandResponseWriter());
  }

  @Test
  public void shouldRejectLookupWhenResponseExceedsMaxMessageSize() {
    // given
    final long userTaskKey = createUserTaskWithOversizedVariables();

    // when
    final var rejection = ENGINE.userTaskLookup().withKey(userTaskKey).expectRejection().fetch();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .startsWith(
            "Expected to fetch user task with key '%d', but with 73KB the response is larger"
                    .formatted(userTaskKey)
                + " than the max message size of 64KB. Narrow fetchVariables to the variables you"
                + " need, or read the variables from the user task search.")
        // every variable, largest first: fetchVariables can only name variables to return
        .endsWith(
            "Variables in the response: largest (~32KB), larger (~24KB), large (~16KB),"
                + " small (~6B)");
  }

  @Test
  public void shouldListOnlyRequestedVariablesWhenRejectingLookup() {
    // given
    final long userTaskKey = createUserTaskWithOversizedVariables();

    // when
    final var rejection =
        ENGINE
            .userTaskLookup()
            .withKey(userTaskKey)
            .withFetchVariables("large", "larger", "largest")
            .expectRejection()
            .fetch();

    // then
    assertThat(rejection.getRejectionReason())
        .endsWith("Variables in the response: largest (~32KB), larger (~24KB), large (~16KB)");
  }

  @Test
  public void shouldNotWriteFetchedEventWhenRejectingLookup() {
    // given
    final long userTaskKey = createUserTaskWithOversizedVariables();
    ENGINE.userTaskLookup().withKey(userTaskKey).expectRejection().fetch();

    // when
    ENGINE.userTaskLookup().withKey(userTaskKey).withFetchVariables("small").fetch();

    // then
    assertThat(
            RecordingExporter.userTaskLookupRecords()
                .withRecordKey(userTaskKey)
                .limit(r -> r.getIntent() == UserTaskLookupIntent.FETCHED))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.COMMAND, UserTaskLookupIntent.FETCH),
            tuple(RecordType.COMMAND_REJECTION, UserTaskLookupIntent.FETCH),
            tuple(RecordType.COMMAND, UserTaskLookupIntent.FETCH),
            tuple(RecordType.EVENT, UserTaskLookupIntent.FETCHED));
  }

  @Test
  public void shouldReturnRequestedVariablesWhenNarrowedBelowMaxMessageSize() {
    // given
    final long userTaskKey = createUserTaskWithOversizedVariables();
    ENGINE.userTaskLookup().withKey(userTaskKey).expectRejection().fetch();

    // when
    final var response = lookup(userTaskKey, "small", "large");

    // then
    assertThat(response.getUserTask().getVariables())
        .containsOnly(entry("small", "value"), entry("large", LARGE));
  }

  @Test
  public void shouldReturnResponseCloseToMaxMessageSize() {
    // given
    final long processInstanceKey = createInstance();
    final long userTaskKey = awaitUserTaskKey(processInstanceKey);
    // 62 KB of variables: just under the 64 KB limit once the task is added
    final String almostHalf = kilobytes(31);
    setVariable(processInstanceKey, "first", almostHalf);
    setVariable(processInstanceKey, "second", almostHalf);

    // when
    final var response = lookup(userTaskKey);

    // then
    assertThat(response.getUserTask().getVariables())
        .containsOnly(
            entry("small", "value"), entry("first", almostHalf), entry("second", almostHalf));
  }

  @Test
  public void shouldCompleteUserTaskAfterRejectedLookup() {
    // given
    final long userTaskKey = createUserTaskWithOversizedVariables();
    ENGINE.userTaskLookup().withKey(userTaskKey).expectRejection().fetch();

    // when
    ENGINE.userTask().withKey(userTaskKey).complete();

    // then
    assertThat(
            RecordingExporter.userTaskRecords(UserTaskIntent.COMPLETED)
                .withRecordKey(userTaskKey)
                .exists())
        .isTrue();
  }

  private long createUserTaskWithOversizedVariables() {
    final long processInstanceKey = createInstance();
    final long userTaskKey = awaitUserTaskKey(processInstanceKey);
    setVariable(processInstanceKey, "largest", LARGEST);
    setVariable(processInstanceKey, "larger", LARGER);
    setVariable(processInstanceKey, "large", LARGE);
    return userTaskKey;
  }

  private long createInstance() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .deploy();
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariables(Map.of("small", "value"))
        .create();
  }

  private static long awaitUserTaskKey(final long processInstanceKey) {
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }

  private static void setVariable(
      final long processInstanceKey, final String name, final String value) {
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of(name, value)).update();
  }

  private static UserTaskLookupRecord lookup(
      final long userTaskKey, final String... fetchVariables) {
    final long requestId = REQUEST_IDS.incrementAndGet();
    ENGINE
        .userTaskLookup()
        .withKey(userTaskKey)
        .withFetchVariables(fetchVariables)
        .withRequestId(requestId)
        .fetch();
    return RESPONSES.awaitResponse(requestId);
  }

  private static String kilobytes(final int kilobytes) {
    return "x".repeat((int) ByteValue.ofKilobytes(kilobytes));
  }
}
