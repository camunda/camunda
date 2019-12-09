/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.concurrency.limits.limit.SettableLimit;
import io.zeebe.broker.transport.backpressure.CommandRateLimiter;
import io.zeebe.broker.transport.backpressure.NoopRequestLimiter;
import io.zeebe.broker.transport.backpressure.RequestLimiter;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.util.AtomixLogStorageRule;
import io.zeebe.logstreams.util.SyncLogStream;
import io.zeebe.logstreams.util.SynchronousLogStream;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.ErrorCode;
import io.zeebe.protocol.record.ErrorResponseDecoder;
import io.zeebe.protocol.record.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.record.MessageHeaderEncoder;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockitoAnnotations;

public class CommandApiMessageHandlerTest {
  protected static final RemoteAddress DEFAULT_ADDRESS =
      new RemoteAddressImpl(21, new SocketAddress("foo", 4242));
  protected static final int LOG_STREAM_PARTITION_ID = 1;
  protected static final byte[] JOB_EVENT;
  private static final int REQUEST_ID = 5;

  static {
    final JobRecord jobEvent = new JobRecord().setType(wrapString("test"));

    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[jobEvent.getEncodedLength()]);
    jobEvent.write(buffer, 0);

    JOB_EVENT = buffer.byteArray();
  }

  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(tempFolder).around(actorSchedulerRule);

  private final AtomixLogStorageRule logStorageRule = new AtomixLogStorageRule(tempFolder);
  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final ExecuteCommandRequestEncoder commandRequestEncoder =
      new ExecuteCommandRequestEncoder();
  private BufferingServerOutput serverOutput;
  private SynchronousLogStream logStream;
  private CommandApiMessageHandler messageHandler;
  private final RequestLimiter noneLimiter = new NoopRequestLimiter();

  @Before
  public void setup() {
    final var logName = "test";
    MockitoAnnotations.initMocks(this);

    logStorageRule.open();
    serverOutput = new BufferingServerOutput();
    logStream =
        new SyncLogStream(
            LogStreams.createLogStream()
                .withPartitionId(LOG_STREAM_PARTITION_ID)
                .withLogName(logName)
                .withActorScheduler(actorSchedulerRule.get())
                .withLogName(logName)
                .withLogStorage(logStorageRule.getStorage())
                .build());
    logStorageRule.setPositionListener(logStream::setCommitPosition);

    messageHandler = new CommandApiMessageHandler();
    messageHandler.addPartition(1, logStream.newLogStreamRecordWriter(), noneLimiter);
  }

  @After
  public void cleanUp() {
    logStream.close();
    logStorageRule.close();
  }

  @Test
  public void shouldWriteCommandRequestProtocolVersion() {
    // given
    final short clientProtocolVersion = Protocol.PROTOCOL_VERSION - 1;
    final int writtenLength =
        writeCommandRequestToBuffer(
            buffer,
            LOG_STREAM_PARTITION_ID,
            clientProtocolVersion,
            ValueType.JOB,
            JobIntent.CREATE);

    // when
    final boolean isHandled =
        messageHandler.onRequest(serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, 123);

    // then
    assertThat(isHandled).isTrue();

    final LogStreamReader logStreamReader = logStream.newLogStreamReader();
    waitForAvailableEvent(logStreamReader);

    final LoggedEvent loggedEvent = logStreamReader.next();
    final RecordMetadata eventMetadata = new RecordMetadata();
    loggedEvent.readMetadata(eventMetadata);

    assertThat(eventMetadata.getProtocolVersion()).isEqualTo(clientProtocolVersion);
  }

  @Test
  public void shouldSendErrorMessageIfPartitionNotFound() {
    // given
    final int writtenLength =
        writeCommandRequestToBuffer(buffer, 99, null, ValueType.JOB, JobIntent.CREATE);

    // when
    final boolean isHandled =
        messageHandler.onRequest(
            serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);

    // then
    assertThat(isHandled).isTrue();

    final List<DirectBuffer> sentResponses = serverOutput.getSentResponses();
    assertThat(sentResponses).hasSize(1);

    final ErrorResponseDecoder errorDecoder = serverOutput.getAsErrorResponse(0);

    assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.PARTITION_LEADER_MISMATCH);
  }

  @Test
  public void shouldNotHandleUnknownRequest() {
    // given
    headerEncoder
        .wrap(buffer, 0)
        .blockLength(commandRequestEncoder.sbeBlockLength())
        .schemaId(commandRequestEncoder.sbeSchemaId())
        .templateId(999)
        .version(1);

    // when
    final boolean isHandled =
        messageHandler.onRequest(
            serverOutput, DEFAULT_ADDRESS, buffer, 0, headerEncoder.encodedLength(), REQUEST_ID);

    // then
    assertThat(isHandled).isTrue();

    assertThat(serverOutput.getSentResponses()).hasSize(1);

    final ErrorResponseDecoder errorDecoder = serverOutput.getAsErrorResponse(0);

    assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.INVALID_MESSAGE_TEMPLATE);
  }

  @Test
  public void shouldSendErrorMessageOnRequestWithNewerProtocolVersion() {
    // given
    final int writtenLength =
        writeCommandRequestToBuffer(
            buffer, LOG_STREAM_PARTITION_ID, Short.MAX_VALUE, ValueType.JOB, JobIntent.CREATE);

    // when
    final boolean isHandled =
        messageHandler.onRequest(
            serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);

    // then
    assertThat(isHandled).isTrue();

    assertThat(serverOutput.getSentResponses()).hasSize(1);

    final ErrorResponseDecoder errorDecoder = serverOutput.getAsErrorResponse(0);

    assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.INVALID_CLIENT_VERSION);
  }

  @Test
  public void shouldSendErrorMessageOnInvalidRequest() {
    // given
    // request is invalid because Value type DEPLOYMENT does not match getValue contents, i.e.
    // required
    // values are not present
    final int writtenLength =
        writeCommandRequestToBuffer(
            buffer, LOG_STREAM_PARTITION_ID, null, ValueType.MESSAGE, MessageIntent.PUBLISH);

    // when
    final boolean isHandled =
        messageHandler.onRequest(
            serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);

    // then
    assertThat(isHandled).isTrue();

    assertThat(serverOutput.getSentResponses()).hasSize(1);

    final ErrorResponseDecoder errorDecoder = serverOutput.getAsErrorResponse(0);

    assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.MALFORMED_REQUEST);
  }

  @Test
  public void shouldSendErrorMessageOnUnsupportedRequest() {
    // given
    final int writtenLength =
        writeCommandRequestToBuffer(
            buffer, LOG_STREAM_PARTITION_ID, null, ValueType.SBE_UNKNOWN, Intent.UNKNOWN);

    // when
    final boolean isHandled =
        messageHandler.onRequest(
            serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);

    // then
    assertThat(isHandled).isTrue();

    assertThat(serverOutput.getSentResponses()).hasSize(1);

    final ErrorResponseDecoder errorDecoder = serverOutput.getAsErrorResponse(0);

    assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.UNSUPPORTED_MESSAGE);
  }

  @Test
  public void shouldSendErrorMessageOnRequestLimitReached() {
    // given
    final CommandRateLimiter settableLimiter =
        CommandRateLimiter.builder().limit(new SettableLimit(1)).build(logStream.getPartitionId());
    messageHandler = new CommandApiMessageHandler();
    final int partitionId = 1;
    final LogStreamRecordWriter logStreamWriter = logStream.newLogStreamRecordWriter();
    messageHandler.addPartition(partitionId, logStreamWriter, settableLimiter);
    settableLimiter.tryAcquire(0, 1, null);

    // when
    final int writtenLength =
        writeCommandRequestToBuffer(
            buffer, LOG_STREAM_PARTITION_ID, null, ValueType.JOB, JobIntent.CREATE);
    final boolean isHandled =
        messageHandler.onRequest(
            serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);
    assertThat(isHandled).isTrue();

    // then
    final List<DirectBuffer> sentResponses = serverOutput.getSentResponses();
    assertThat(sentResponses).hasSize(1);

    final ErrorResponseDecoder errorDecoder = serverOutput.getAsErrorResponse(0);

    assertThat(errorDecoder.errorCode()).isEqualTo(ErrorCode.RESOURCE_EXHAUSTED);
  }

  @Test
  public void shouldNotSendErrorMessageOnRequestLimitReachedIfJobComplete() {
    // given
    final CommandRateLimiter settableLimiter =
        CommandRateLimiter.builder().limit(new SettableLimit(1)).build(logStream.getPartitionId());
    messageHandler = new CommandApiMessageHandler();
    final int partitionId = 1;
    final LogStreamRecordWriter logStreamWriter = logStream.newLogStreamRecordWriter();
    messageHandler.addPartition(partitionId, logStreamWriter, settableLimiter);
    settableLimiter.tryAcquire(0, 1, null);

    // when
    final int writtenLength =
        writeCommandRequestToBuffer(
            buffer, LOG_STREAM_PARTITION_ID, null, ValueType.JOB, JobIntent.COMPLETE);
    final boolean isHandled =
        messageHandler.onRequest(
            serverOutput, DEFAULT_ADDRESS, buffer, 0, writtenLength, REQUEST_ID);
    assertThat(isHandled).isTrue();

    // then
    final List<DirectBuffer> sentResponses = serverOutput.getSentResponses();
    assertThat(sentResponses).hasSize(0);
  }

  protected int writeCommandRequestToBuffer(
      final UnsafeBuffer buffer,
      final int partitionId,
      final Short protocolVersion,
      final ValueType type,
      final Intent intent) {
    int offset = 0;

    final int protocolVersionToWrite =
        protocolVersion != null ? protocolVersion : commandRequestEncoder.sbeSchemaVersion();
    final ValueType eventTypeToWrite = type != null ? type : ValueType.NULL_VAL;

    headerEncoder
        .wrap(buffer, offset)
        .blockLength(commandRequestEncoder.sbeBlockLength())
        .schemaId(commandRequestEncoder.sbeSchemaId())
        .templateId(commandRequestEncoder.sbeTemplateId())
        .version(protocolVersionToWrite);

    offset += headerEncoder.encodedLength();

    commandRequestEncoder.wrap(buffer, offset);

    commandRequestEncoder
        .partitionId(partitionId)
        .valueType(eventTypeToWrite)
        .intent(intent.value())
        .putValue(JOB_EVENT, 0, JOB_EVENT.length);

    return headerEncoder.encodedLength() + commandRequestEncoder.encodedLength();
  }

  private void waitForAvailableEvent(final LogStreamReader logStreamReader) {
    TestUtil.waitUntil(logStreamReader::hasNext);
  }
}
