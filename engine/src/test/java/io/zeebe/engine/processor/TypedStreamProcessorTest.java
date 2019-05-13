/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.util.RecordStream;
import io.zeebe.engine.util.Records;
import io.zeebe.engine.util.StreamProcessorControl;
import io.zeebe.engine.util.TestStreams;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockitoAnnotations;

public class TypedStreamProcessorTest {
  public static final String STREAM_NAME = "foo";
  public static final int STREAM_PROCESSOR_ID = 144144;

  public TemporaryFolder tempFolder = new TemporaryFolder();
  public AutoCloseableRule closeables = new AutoCloseableRule();

  public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(tempFolder)
          .around(actorSchedulerRule)
          .around(serviceContainerRule)
          .around(closeables);

  protected TestStreams streams;
  protected LogStream stream;

  private StreamProcessorControl streamProcessorControl;
  private KeyGenerator keyGenerator;
  private TypedStreamEnvironment env;
  private CommandResponseWriter mockCommandResponseWriter;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    mockCommandResponseWriter = mock(CommandResponseWriter.class);
    when(mockCommandResponseWriter.intent(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.key(anyLong())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.partitionId(anyInt())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.recordType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.rejectionType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.rejectionReason(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.valueType(any())).thenReturn(mockCommandResponseWriter);
    when(mockCommandResponseWriter.valueWriter(any())).thenReturn(mockCommandResponseWriter);

    when(mockCommandResponseWriter.tryWriteResponse(anyInt(), anyLong())).thenReturn(true);

    streams =
        new TestStreams(
            tempFolder, closeables, serviceContainerRule.get(), actorSchedulerRule.get());

    stream = streams.createLogStream(STREAM_NAME);
    env = new TypedStreamEnvironment(streams.getLogStream(STREAM_NAME), mockCommandResponseWriter);

    final AtomicLong key = new AtomicLong();
    keyGenerator = () -> key.getAndIncrement();
  }

  @Test
  public void shouldWriteSourceEventAndProducerOnBatch() {
    // given
    streamProcessorControl =
        streams.initStreamProcessor(
            STREAM_NAME,
            STREAM_PROCESSOR_ID,
            DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
            (actor, db, dbContext) ->
                env.newStreamProcessor()
                    .zeebeState(new ZeebeState(db, dbContext))
                    .onCommand(ValueType.DEPLOYMENT, DeploymentIntent.CREATE, new BatchProcessor())
                    .build());
    streamProcessorControl.start();
    final long firstEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(deployment("foo", ResourceType.BPMN_XML))
            .recordType(RecordType.COMMAND)
            .intent(DeploymentIntent.CREATE)
            .write();

    // when
    streamProcessorControl.unblock();

    final LoggedEvent writtenEvent =
        TestUtil.doRepeatedly(
                () ->
                    streams
                        .events(STREAM_NAME)
                        .filter(
                            e -> Records.isEvent(e, ValueType.DEPLOYMENT, DeploymentIntent.CREATED))
                        .findFirst())
            .until(o -> o.isPresent())
            .get();

    // then
    assertThat(writtenEvent.getProducerId()).isEqualTo(STREAM_PROCESSOR_ID);

    assertThat(writtenEvent.getSourceEventPosition()).isEqualTo(firstEventPosition);
  }

  @Test
  public void shouldSkipFailingEvent() {
    // given
    streamProcessorControl =
        streams.initStreamProcessor(
            STREAM_NAME,
            STREAM_PROCESSOR_ID,
            DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
            (actor, db, dbContext) ->
                env.newStreamProcessor()
                    .zeebeState(new ZeebeState(db, dbContext))
                    .onCommand(
                        ValueType.DEPLOYMENT, DeploymentIntent.CREATE, new ErrorProneProcessor())
                    .build());
    streamProcessorControl.start();
    final AtomicLong requestId = new AtomicLong(0);
    final AtomicInteger requestStreamId = new AtomicInteger(0);

    when(mockCommandResponseWriter.tryWriteResponse(anyInt(), anyLong()))
        .then(
            (invocationOnMock -> {
              final int streamIdArg = invocationOnMock.getArgument(0);
              final long requestIdArg = invocationOnMock.getArgument(1);

              requestId.set(requestIdArg);
              requestStreamId.set(streamIdArg);

              return true;
            }));

    final long failingKey = keyGenerator.nextKey();
    streams
        .newRecord(STREAM_NAME)
        .event(deployment("foo", ResourceType.BPMN_XML))
        .recordType(RecordType.COMMAND)
        .intent(DeploymentIntent.CREATE)
        .requestId(255L)
        .requestStreamId(99)
        .key(failingKey)
        .write();
    final long secondEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(deployment("foo2", ResourceType.BPMN_XML))
            .recordType(RecordType.COMMAND)
            .intent(DeploymentIntent.CREATE)
            .key(keyGenerator.nextKey())
            .write();

    // when
    streamProcessorControl.unblock();

    final LoggedEvent writtenEvent =
        TestUtil.doRepeatedly(
                () ->
                    streams
                        .events(STREAM_NAME)
                        .filter(
                            e -> Records.isEvent(e, ValueType.DEPLOYMENT, DeploymentIntent.CREATED))
                        .findFirst())
            .until(o -> o.isPresent())
            .get();

    // then
    assertThat(writtenEvent.getKey()).isEqualTo(1);
    assertThat(writtenEvent.getProducerId()).isEqualTo(STREAM_PROCESSOR_ID);
    assertThat(writtenEvent.getSourceEventPosition()).isEqualTo(secondEventPosition);

    // error response
    verify(mockCommandResponseWriter).tryWriteResponse(anyInt(), anyLong());

    assertThat(requestId.get()).isEqualTo(255L);
    assertThat(requestStreamId.get()).isEqualTo(99);

    final TypedRecord<DeploymentRecord> deploymentRejection =
        new RecordStream(streams.events(STREAM_NAME))
            .onlyDeploymentRecords()
            .onlyRejections()
            .withIntent(DeploymentIntent.CREATE)
            .getFirst();

    assertThat(deploymentRejection.getKey()).isEqualTo(failingKey);
    assertThat(deploymentRejection.getMetadata().getRejectionType())
        .isEqualTo(RejectionType.PROCESSING_ERROR);
  }

  protected DeploymentRecord deployment(final String name, final ResourceType resourceType) {
    final DeploymentRecord event = new DeploymentRecord();
    event
        .resources()
        .add()
        .setResourceType(resourceType)
        .setResource(wrapString("foo"))
        .setResourceName(wrapString(name));
    return event;
  }

  protected static class ErrorProneProcessor implements TypedRecordProcessor<DeploymentRecord> {

    @Override
    public void processRecord(
        final TypedRecord<DeploymentRecord> record,
        final TypedResponseWriter responseWriter,
        final TypedStreamWriter streamWriter) {
      if (record.getKey() == 0) {
        throw new RuntimeException("expected");
      }
      streamWriter.appendFollowUpEvent(
          record.getKey(), DeploymentIntent.CREATED, record.getValue());
      streamWriter.flush();
    }
  }

  protected class BatchProcessor implements TypedRecordProcessor<DeploymentRecord> {
    @Override
    public void processRecord(
        final TypedRecord<DeploymentRecord> record,
        final TypedResponseWriter responseWriter,
        final TypedStreamWriter streamWriter) {
      streamWriter.appendNewEvent(
          keyGenerator.nextKey(), DeploymentIntent.CREATED, record.getValue());
      streamWriter.flush();
    }
  }
}
