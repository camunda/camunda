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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.util.RecordStream;
import io.zeebe.engine.util.Records;
import io.zeebe.engine.util.TestStreams;
import io.zeebe.protocol.record.value.deployment.ResourceType;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.RecordType;
import io.zeebe.protocol.RejectionType;
import io.zeebe.protocol.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.intent.DeploymentIntent;
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
  private static final String STREAM_NAME = "foo";
  private static final int STREAM_PROCESSOR_ID = 144144;

  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final AutoCloseableRule closeables = new AutoCloseableRule();

  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  private final ServiceContainerRule serviceContainerRule =
      new ServiceContainerRule(actorSchedulerRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(tempFolder)
          .around(actorSchedulerRule)
          .around(serviceContainerRule)
          .around(closeables);

  private TestStreams streams;
  protected LogStream stream;

  private KeyGenerator keyGenerator;
  private CommandResponseWriter mockCommandResponseWriter;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    streams =
        new TestStreams(
            tempFolder, closeables, serviceContainerRule.get(), actorSchedulerRule.get());
    mockCommandResponseWriter = streams.getMockedResponseWriter();
    stream = streams.createLogStream(STREAM_NAME);

    final AtomicLong key = new AtomicLong();
    keyGenerator = () -> key.getAndIncrement();
  }

  @Test
  public void shouldWriteSourceEventAndProducerOnBatch() {
    // given
    streams.startStreamProcessor(
        STREAM_NAME,
        STREAM_PROCESSOR_ID,
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
        (processingContext) ->
            TypedRecordProcessors.processors()
                .onCommand(ValueType.DEPLOYMENT, DeploymentIntent.CREATE, new BatchProcessor()));
    final long firstEventPosition =
        streams
            .newRecord(STREAM_NAME)
            .event(deployment("foo", ResourceType.BPMN_XML))
            .recordType(RecordType.COMMAND)
            .intent(DeploymentIntent.CREATE)
            .write();

    // when
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
    streams.startStreamProcessor(
        STREAM_NAME,
        STREAM_PROCESSOR_ID,
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
        (processingContext) ->
            TypedRecordProcessors.processors()
                .onCommand(
                    ValueType.DEPLOYMENT, DeploymentIntent.CREATE, new ErrorProneProcessor()));
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
