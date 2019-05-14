/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.transport.clientapi;

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.partitions.RaftState;
import io.zeebe.distributedlog.DistributedLogstreamService;
import io.zeebe.distributedlog.impl.DefaultDistributedLogstreamService;
import io.zeebe.distributedlog.impl.DistributedLogstreamPartition;
import io.zeebe.distributedlog.impl.DistributedLogstreamServiceConfig;
import io.zeebe.logstreams.LogStreams;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.protocol.clientapi.ErrorResponseDecoder;
import io.zeebe.protocol.clientapi.ExecuteCommandRequestEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.RemoteAddressImpl;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.File;
import java.io.IOException;
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

public class ClientApiMessageHandlerTest {
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
  private ClientApiMessageHandler messageHandler;
  private DistributedLogstreamService distributedLogImpl;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    serverOutput = new BufferingServerOutput();
    File runtime = null;
    File snapshots = null;

    try {
      runtime = tempFolder.newFolder("index", "runtime");
      snapshots = tempFolder.newFolder("index", "snapshots");
    } catch (IOException e) {
      e.printStackTrace();
    }

    final StateStorage stateStorage = new StateStorage(runtime, snapshots);

    final String logName = "test";

    logStream =
        LogStreams.createFsLogStream(LOG_STREAM_PARTITION_ID)
            .logRootPath(tempFolder.getRoot().getAbsolutePath())
            .serviceContainer(serviceContainerRule.get())
            .logName(logName)
            .indexStateStorage(stateStorage)
            .build()
            .join();

    // Create distributed log service
    final DistributedLogstreamPartition mockDistLog = mock(DistributedLogstreamPartition.class);

    distributedLogImpl =
        new DefaultDistributedLogstreamService(new DistributedLogstreamServiceConfig());

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

    messageHandler = new ClientApiMessageHandler();

    final Partition partition =
        new Partition(
            null,
            mock(ClusterEventService.class),
            mock(ClusterCommunicationService.class),
            LOG_STREAM_PARTITION_ID,
            RaftState.LEADER) {
          @Override
          public LogStream getLogStream() {
            return logStream;
          }
        };

    messageHandler.addPartition(partition);
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
