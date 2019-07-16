/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.zeebe.distributedlog.DistributedLogstreamService;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamService;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
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
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.FieldSetter;
import org.mockito.stubbing.Answer;

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

  protected final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024 * 1024]);
  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final ExecuteCommandRequestEncoder commandRequestEncoder =
      new ExecuteCommandRequestEncoder();
  public TemporaryFolder tempFolder = new TemporaryFolder();
  public ActorSchedulerRule agentRunnerService = new ActorSchedulerRule();
  public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(agentRunnerService);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(tempFolder).around(agentRunnerService).around(serviceContainerRule);

  protected BufferingServerOutput serverOutput;
  private LogStream logStream;
  private CommandApiMessageHandler messageHandler;
  private DistributedLogstreamService distributedLogImpl;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    serverOutput = new BufferingServerOutput();
    final String logName = "test";
    logStream =
        LogStreams.createFsLogStream(LOG_STREAM_PARTITION_ID)
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .serviceContainer(serviceContainerRule.get())
            .logName(logName)
            .build()
            .join();

    // Create distributed log service
    final DistributedLogstreamPartition mockDistLog = mock(DistributedLogstreamPartition.class);

    distributedLogImpl = new DefaultDistributedLogstreamService();

    final String nodeId = "0";
    try {
      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("logStream"),
          logStream);

      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("logStorage"),
          logStream.getLogStorage());

      FieldSetter.setField(
          distributedLogImpl,
          DefaultDistributedLogstreamService.class.getDeclaredField("currentLeader"),
          nodeId);

    } catch (NoSuchFieldException e) {
      e.printStackTrace();
    }
    doAnswer(
            (Answer<CompletableFuture<Long>>)
                invocation -> {
                  final Object[] arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length > 1
                      && arguments[0] != null
                      && arguments[1] != null) {
                    final byte[] bytes = (byte[]) arguments[0];
                    final long pos = (long) arguments[1];
                    return CompletableFuture.completedFuture(
                        distributedLogImpl.append(nodeId, pos, bytes));
                  }
                  return null;
                })
        .when(mockDistLog)
        .asyncAppend(any(byte[].class), anyLong());

    serviceContainerRule
        .get()
        .createService(distributedLogPartitionServiceName(logName), () -> mockDistLog)
        .install()
        .join();

    logStream.openAppender().join();

    messageHandler = new CommandApiMessageHandler();
    messageHandler.addPartition(logStream);
  }

  @After
  public void cleanUp() {
    logStream.close();
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

    final BufferedLogStreamReader logStreamReader = new BufferedLogStreamReader(logStream);
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

  protected void waitForAvailableEvent(final BufferedLogStreamReader logStreamReader) {
    TestUtil.waitUntil(() -> logStreamReader.hasNext());
  }
}
