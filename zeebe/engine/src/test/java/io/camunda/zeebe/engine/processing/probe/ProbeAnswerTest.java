/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.protocol.record.value.ProbeEndReason;
import io.camunda.zeebe.protocol.record.value.ProbeOutcome;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.ByteValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class ProbeAnswerTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final long MAX_MESSAGE_SIZE = ByteValue.ofMegabytes(4);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String probeId;

  @Before
  public void setUp() {
    probeId = "validate-configuration/io.camunda:test-" + UUID.randomUUID() + ":1";
  }

  @Test
  public void shouldAnswerTheClientWithTheDataAndKeepItOffTheEvent() {
    // given
    final long probeKey =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequest(11, 111L)
            .create()
            .getKey();
    ENGINE.probe().activate(List.of(probeId), "connectors-runtime", 1);

    // when
    final var answered =
        ENGINE.probe().withRequest(12, 121L).answer(probeKey, ok(Map.of("status", "SUCCESS")));

    // then
    assertThat(answered.getIntent()).isEqualTo(ProbeIntent.ANSWERED);
    assertThat(answered.getKey()).isEqualTo(probeKey);
    assertThat(answered.getValue().getOutcome()).isEqualTo(ProbeOutcome.OK);
    assertThat(answered.getValue().getWorker())
        .describedAs("the worker of the poll that collected the probe")
        .isEqualTo("connectors-runtime");
    assertThat(answered.getValue().getData()).describedAs("never the data (D42)").isEmpty();
    assertThat(answered.getValue().getInput()).isEmpty();
    final var respond =
        RecordingExporter.probeRecords(ProbeIntent.RESPOND).withRecordKey(probeKey).getFirst();
    assertThat(respond.getValue().getData())
        .describedAs("the internal command that answers the client carries the data (D74)")
        .isEqualTo(Map.of("status", "SUCCESS"));
    assertThat(respond.getValue().getWorker()).isEqualTo("connectors-runtime");
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(12, 121L);
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(11, 111L);
  }

  @Test
  public void shouldRejectARespondSentByAClient() {
    // given
    final long probeKey = activatedProbe();

    // when
    final var rejection =
        ENGINE.probe().expectRejection().respond(probeKey, ok(Map.of("status", "SUCCESS")));

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected RESPOND to be written by the engine, but it was sent by a client.");
  }

  @Test
  public void shouldCarryTheMessageOfAnErrorAnswer() {
    // given
    final long probeKey = activatedProbe();

    // when
    final var answered = ENGINE.probe().answer(probeKey, error("The handler failed."));

    // then
    assertThat(answered.getValue().getOutcome()).isEqualTo(ProbeOutcome.ERROR);
    assertThat(answered.getValue().getMessage()).isEqualTo("The handler failed.");
  }

  @Test
  public void shouldAcceptAnUnsupportedAnswer() {
    // given
    final long probeKey = activatedProbe();

    // when
    final var answered = ENGINE.probe().answer(probeKey, unsupported());

    // then
    assertThat(answered.getValue().getOutcome()).isEqualTo(ProbeOutcome.UNSUPPORTED);
    assertThat(answered.getValue().getMessage()).isEmpty();
  }

  @Test
  public void shouldEndTheProbeWhenAnswered() {
    // given
    final long probeKey = activatedProbe();
    ENGINE.probe().answer(probeKey, unsupported());

    // when
    final var rejection = ENGINE.probe().expectRejection().answer(probeKey, unsupported());

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldAcceptAMessageOf500Characters() {
    // given
    final long probeKey = activatedProbe();

    // when
    final var answered = ENGINE.probe().answer(probeKey, error("m".repeat(500)));

    // then
    assertThat(answered.getIntent()).isEqualTo(ProbeIntent.ANSWERED);
  }

  @Test
  public void shouldRejectAnErrorAnswerWithData() {
    assertRejectedEnvelope(error("failed").setData(data(Map.of("status", "FAILURE"))));
  }

  @Test
  public void shouldAnswerTheClientWithEmptyData() {
    // given
    final long probeKey =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequest(13, 131L)
            .create()
            .getKey();
    ENGINE.probe().activate(List.of(probeId), "worker", 1);

    // when the worker's data is an empty object, as its probe ID may define it (D9)
    final var answered = ENGINE.probe().answer(probeKey, ok(Map.of()));

    // then
    assertThat(answered.getIntent()).isEqualTo(ProbeIntent.ANSWERED);
    assertThat(answered.getValue().getOutcome()).isEqualTo(ProbeOutcome.OK);
    final var respond =
        RecordingExporter.probeRecords(ProbeIntent.RESPOND).withRecordKey(probeKey).getFirst();
    assertThat(respond.getValue().getOutcome()).isEqualTo(ProbeOutcome.OK);
    assertThat(respond.getValue().getData()).isEmpty();
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(13, 131L);
  }

  @Test
  public void shouldRejectAnOkAnswerWithAMessage() {
    assertRejectedEnvelope(ok(Map.of("status", "SUCCESS")).setMessage("all good"));
  }

  @Test
  public void shouldRejectAnUnsupportedAnswerWithAMessage() {
    assertRejectedEnvelope(unsupported().setMessage("not for this input"));
  }

  @Test
  public void shouldRejectAnErrorAnswerWithoutMessage() {
    assertRejectedEnvelope(new ProbeRecord().setOutcome(ProbeOutcome.ERROR));
  }

  @Test
  public void shouldRejectAMessageLongerThan500Characters() {
    assertRejectedEnvelope(error("m".repeat(501)));
  }

  @Test
  public void shouldRejectAnAnswerWithoutOutcome() {
    assertRejectedEnvelope(new ProbeRecord());
  }

  @Test
  public void shouldRejectAnAnswerToAProbeThatIsNotActivated() {
    // given
    final long probeKey = ENGINE.probe().withProbeId(probeId).withInput("={}").create().getKey();

    // when
    final var rejection = ENGINE.probe().expectRejection().answer(probeKey, unsupported());

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to answer probe with key '%d', but it is in state 'ACTIVATABLE'."
                .formatted(probeKey));
  }

  @Test
  public void shouldCheckTheEnvelopeBeforeTheState() {
    // given
    final long probeKey = ENGINE.probe().withProbeId(probeId).withInput("={}").create().getKey();

    // when
    final var rejection = ENGINE.probe().expectRejection().answer(probeKey, new ProbeRecord());

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldEndWithWorkerDidNotAnswerAtTheDeadlineAndRejectALateAnswer() {
    // given
    final long probeKey =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequestTimeout(1_000)
            .withRequest(44, 444L)
            .create()
            .getKey();
    ENGINE.probe().activate(List.of(probeId), "connectors-runtime", 1);

    // when
    ENGINE.increaseTime(Duration.ofSeconds(2));

    // then
    final var timedOut =
        RecordingExporter.probeRecords(ProbeIntent.TIMED_OUT).withRecordKey(probeKey).getFirst();
    assertThat(timedOut.getValue().getEndReason()).isEqualTo(ProbeEndReason.WORKER_DID_NOT_ANSWER);
    assertThat(timedOut.getValue().getWorker()).isEqualTo("connectors-runtime");
    assertThat(timedOut.getValue().getMessage())
        .isEqualTo(
            "The worker 'connectors-runtime' collected the probe for '%s' but did not answer within 1000 ms."
                .formatted(probeId));
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(44, 444L);

    // and a late answer, even a well-formed one, gets NOT_FOUND (F4)
    final var lateAnswer =
        ENGINE.probe().expectRejection().answer(probeKey, ok(Map.of("status", "SUCCESS")));
    Assertions.assertThat(lateAnswer)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to answer probe with key '%d', but no such probe was found."
                .formatted(probeKey));
  }

  @Test
  public void shouldRespondToTheGatewaysYield() {
    // given
    final long probeKey = activatedProbe();

    // when
    final var yielded = ENGINE.probe().withRequest(48, 488L).yieldAsRequest(probeKey);

    // then
    assertThat(yielded.getIntent()).isEqualTo(ProbeIntent.YIELDED);
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(48, 488L);
  }

  @Test
  public void shouldRespondToARejectedYield() {
    // given - the probe is activatable again after a first yield
    final long probeKey = activatedProbe();
    ENGINE.probe().yield(probeKey);

    // when
    final var rejection =
        ENGINE.probe().withRequest(49, 499L).expectRejection().yieldAsRequest(probeKey);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_STATE);
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(49, 499L);
  }

  @Test
  public void shouldEndWithNoWorkerAtTheDeadlineAfterAYield() {
    // given
    final long probeKey =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequestTimeout(1_000)
            .create()
            .getKey();
    ENGINE.probe().activate(List.of(probeId), "connectors-runtime", 1);
    ENGINE.probe().yield(probeKey);

    // when
    ENGINE.increaseTime(Duration.ofSeconds(2));

    // then
    final var timedOut =
        RecordingExporter.probeRecords(ProbeIntent.TIMED_OUT).withRecordKey(probeKey).getFirst();
    assertThat(timedOut.getValue().getEndReason()).isEqualTo(ProbeEndReason.NO_WORKER);
    assertThat(timedOut.getValue().getWorker())
        .describedAs("no worker holds the probe after the yield")
        .isEmpty();
  }

  @Test
  public void shouldRejectAnAnswerProcessedAfterTheDeadlineBeforeItsTimeOut() {
    // given
    final long probeKey =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequestTimeout(1_000)
            .withRequest(45, 455L)
            .create()
            .getKey();
    ENGINE.probe().activate(List.of(probeId), "connectors-runtime", 1);

    // when the answer is in the log before the TIME_OUT, but processed after the deadline
    ENGINE.pauseProcessing(1);
    try {
      ENGINE.writeCommandOnPartition(
          1, probeKey, ProbeIntent.ANSWER, ok(Map.of("status", "SUCCESS")));
      ENGINE.increaseTime(Duration.ofSeconds(2));
    } finally {
      ENGINE.resumeProcessing(1);
    }

    // then the answer is late (F4), and the client keeps the time-out the deadline promised
    final var rejection =
        RecordingExporter.probeRecords(ProbeIntent.ANSWER)
            .onlyCommandRejections()
            .withRecordKey(probeKey)
            .getFirst();
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to answer probe with key '%d', but its deadline has passed."
                .formatted(probeKey));
    final var timedOut =
        RecordingExporter.probeRecords(ProbeIntent.TIMED_OUT).withRecordKey(probeKey).getFirst();
    assertThat(timedOut.getValue().getEndReason()).isEqualTo(ProbeEndReason.WORKER_DID_NOT_ANSWER);
    assertThat(
            RecordingExporter.probeRecords()
                .withRecordKey(probeKey)
                .limit(r -> r.getIntent() == ProbeIntent.TIMED_OUT)
                .withIntent(ProbeIntent.ANSWERED)
                .exists())
        .isFalse();
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(45, 455L);
  }

  @Test
  public void shouldRejectAYieldProcessedAfterTheDeadlineBeforeItsTimeOut() {
    // given
    final long probeKey =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequestTimeout(1_000)
            .create()
            .getKey();
    ENGINE.probe().activate(List.of(probeId), "connectors-runtime", 1);

    // when the yield is in the log before the TIME_OUT, but processed after the deadline
    ENGINE.pauseProcessing(1);
    try {
      ENGINE.writeCommandOnPartition(1, probeKey, ProbeIntent.YIELD, new ProbeRecord());
      ENGINE.increaseTime(Duration.ofSeconds(2));
    } finally {
      ENGINE.resumeProcessing(1);
    }

    // then the probe stays with its worker, and ends as one the worker did not answer
    final var rejection =
        RecordingExporter.probeRecords(ProbeIntent.YIELD)
            .onlyCommandRejections()
            .withRecordKey(probeKey)
            .getFirst();
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to yield probe with key '%d', but its deadline has passed."
                .formatted(probeKey));
    final var timedOut =
        RecordingExporter.probeRecords(ProbeIntent.TIMED_OUT).withRecordKey(probeKey).getFirst();
    assertThat(timedOut.getValue().getEndReason()).isEqualTo(ProbeEndReason.WORKER_DID_NOT_ANSWER);
  }

  @Test
  public void shouldAnswerTheClientWithDataNearTheMaxMessageSize() {
    // given
    final long probeKey =
        ENGINE
            .probe()
            .withProbeId(probeId)
            .withInput("={}")
            .withRequest(46, 466L)
            .create()
            .getKey();
    ENGINE.probe().activate(List.of(probeId), "worker", 1);
    final var nearMax = ok(Map.of("blob", "x".repeat((int) (MAX_MESSAGE_SIZE - 20 * 1024))));

    // when
    final var answered = ENGINE.probe().answer(probeKey, nearMax);

    // then the RESPOND that carries the data again still fits the batch of the ANSWER
    assertThat(answered.getIntent()).isEqualTo(ProbeIntent.ANSWERED);
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(46, 466L);
  }

  @Test
  public void shouldRejectAnAnswerWhoseRespondDoesNotFitTheBatchAndKeepTheProbeActivated() {
    // given a worker name long enough that ANSWERED and RESPOND outgrow the ANSWER they follow
    final long probeKey = ENGINE.probe().withProbeId(probeId).withInput("={}").create().getKey();
    ENGINE.probe().activate(List.of(probeId), "w".repeat(20 * 1024), 1);
    final var nearMax = ok(Map.of("blob", "x".repeat((int) (MAX_MESSAGE_SIZE - 20 * 1024))));

    // when
    final var rejection =
        ENGINE.probe().withRequest(47, 477L).expectRejection().answer(probeKey, nearMax);

    // then the worker gets the rejection the REST API maps to 413 (D53), and nothing was written
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.EXCEEDED_BATCH_RECORD_SIZE);
    verify(ENGINE.getCommandResponseWriter(), timeout(5000)).tryWriteResponse(47, 477L);
    assertThat(ENGINE.probe().answer(probeKey, unsupported()).getIntent())
        .describedAs("the probe is still activated, so the worker can answer ERROR instead")
        .isEqualTo(ProbeIntent.ANSWERED);
  }

  private long activatedProbe() {
    final long probeKey = ENGINE.probe().withProbeId(probeId).withInput("={}").create().getKey();
    ENGINE.probe().activate(List.of(probeId), "worker", 1);
    return probeKey;
  }

  private void assertRejectedEnvelope(final ProbeRecord answer) {
    // given
    final long probeKey = activatedProbe();

    // when
    final var rejection = ENGINE.probe().expectRejection().answer(probeKey, answer);

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .startsWith(
            "Expected an answer of outcome OK without message, UNSUPPORTED with neither data"
                + " nor message, or ERROR with a message of at most 500 characters");
    // the probe stays activated: a valid answer is still accepted
    assertThat(ENGINE.probe().answer(probeKey, unsupported()).getIntent())
        .isEqualTo(ProbeIntent.ANSWERED);
  }

  private static ProbeRecord ok(final Map<String, Object> data) {
    return new ProbeRecord().setOutcome(ProbeOutcome.OK).setData(data(data));
  }

  private static ProbeRecord unsupported() {
    return new ProbeRecord().setOutcome(ProbeOutcome.UNSUPPORTED);
  }

  private static ProbeRecord error(final String message) {
    return new ProbeRecord().setOutcome(ProbeOutcome.ERROR).setMessage(message);
  }

  private static DirectBuffer data(final Map<String, Object> data) {
    return BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(data));
  }
}
