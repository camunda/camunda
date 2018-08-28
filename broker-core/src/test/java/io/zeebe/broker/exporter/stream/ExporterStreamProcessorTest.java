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
package io.zeebe.broker.exporter.stream;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.zeebe.broker.clustering.orchestration.id.IdRecord;
import io.zeebe.broker.clustering.orchestration.topic.TopicRecord;
import io.zeebe.broker.exporter.record.value.DeploymentRecordValueImpl;
import io.zeebe.broker.exporter.record.value.IdRecordValueImpl;
import io.zeebe.broker.exporter.record.value.IncidentRecordValueImpl;
import io.zeebe.broker.exporter.record.value.JobRecordValueImpl;
import io.zeebe.broker.exporter.record.value.MessageRecordValueImpl;
import io.zeebe.broker.exporter.record.value.MessageSubscriptionRecordValueImpl;
import io.zeebe.broker.exporter.record.value.RaftRecordValueImpl;
import io.zeebe.broker.exporter.record.value.TopicRecordValueImpl;
import io.zeebe.broker.exporter.record.value.WorkflowInstanceRecordValueImpl;
import io.zeebe.broker.exporter.record.value.WorkflowInstanceSubscriptionRecordValueImpl;
import io.zeebe.broker.exporter.record.value.deployment.DeployedWorkflowImpl;
import io.zeebe.broker.exporter.record.value.deployment.DeploymentResourceImpl;
import io.zeebe.broker.exporter.record.value.job.HeadersImpl;
import io.zeebe.broker.exporter.record.value.raft.RaftMemberImpl;
import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.broker.exporter.stream.ExporterRecord.ExporterPosition;
import io.zeebe.broker.exporter.util.ControlledTestExporter;
import io.zeebe.broker.exporter.util.PojoConfigurationExporter;
import io.zeebe.broker.exporter.util.PojoConfigurationExporter.PojoExporterConfiguration;
import io.zeebe.broker.incident.data.ErrorType;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.job.data.JobRecord;
import io.zeebe.broker.subscription.message.data.MessageRecord;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.system.workflow.repository.data.DeploymentRecord;
import io.zeebe.broker.system.workflow.repository.data.ResourceType;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordMetadata;
import io.zeebe.exporter.record.RecordValue;
import io.zeebe.exporter.record.value.DeploymentRecordValue;
import io.zeebe.exporter.record.value.IdRecordValue;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.exporter.record.value.MessageRecordValue;
import io.zeebe.exporter.record.value.MessageSubscriptionRecordValue;
import io.zeebe.exporter.record.value.RaftRecordValue;
import io.zeebe.exporter.record.value.TopicRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.gateway.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.ExporterIntent;
import io.zeebe.protocol.intent.IdIntent;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.RaftIntent;
import io.zeebe.protocol.intent.TopicIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.raft.event.RaftConfigurationEvent;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

public class ExporterStreamProcessorTest {
  private static final int PARTITION_ID = 1;
  private static final ZeebeObjectMapperImpl OBJECT_MAPPER = new ZeebeObjectMapperImpl();
  private static final Map<String, Object> PAYLOAD = Collections.singletonMap("foo", "bar");
  private static final String PAYLOAD_JSON = OBJECT_MAPPER.toJson(PAYLOAD);
  private static final DirectBuffer PAYLOAD_MSGPACK =
      new UnsafeBuffer(OBJECT_MAPPER.toMsgpack(PAYLOAD));
  private static final Map<String, Object> CUSTOM_HEADERS =
      Collections.singletonMap("workerVersion", 42);
  private static final DirectBuffer CUSTOM_HEADERS_MSGPACK =
      new UnsafeBuffer(OBJECT_MAPPER.toMsgpack(CUSTOM_HEADERS));

  @Rule public StreamProcessorRule rule = new StreamProcessorRule();

  private List<ControlledTestExporter> exporters;

  @Test
  public void shouldConfigureAllExportersProperlyOnStart() {
    // given
    final Map[] arguments = new Map[] {newConfig("foo", "bar"), newConfig("bar", "foo")};
    final List<ExporterDescriptor> descriptors = createMockedExporters(arguments);
    final ExporterStreamProcessor processor =
        new ExporterStreamProcessor(PARTITION_ID, descriptors);

    // when
    final StreamProcessorControl control = rule.initStreamProcessor(e -> processor);
    control.start();

    // then
    for (int i = 0; i < exporters.size(); i++) {
      assertThat(exporters.get(i).getContext().getConfiguration().getId())
          .isEqualTo(descriptors.get(i).getId());
      assertThat(exporters.get(i).getContext().getConfiguration().getArguments())
          .isEqualTo(arguments[i]);
      assertThat(exporters.get(i).getContext().getLogger()).isNotNull();
      assertThat(exporters.get(i).getController()).isNotNull();
    }
  }

  @Test
  public void shouldInstantiateConfigurationClass() {
    // given
    final String foo = "bar";
    final int x = 123;
    final String bar = "baz";
    final double y = 32.12;

    final Map<String, Object> nested = new HashMap<>();
    nested.put("bar", bar);
    nested.put("y", y);

    final Map<String, Object> arguments = new HashMap<>();
    arguments.put("foo", foo);
    arguments.put("x", x);
    arguments.put("nested", nested);

    final ExporterDescriptor descriptor =
        new ExporterDescriptor(
            "instantiateConfiguration", PojoConfigurationExporter.class, arguments);

    final ExporterStreamProcessor processor =
        new ExporterStreamProcessor(PARTITION_ID, Collections.singletonList(descriptor));

    rule.runStreamProcessor(e -> processor);

    // then
    final PojoExporterConfiguration configuration = PojoConfigurationExporter.configuration;

    assertThat(configuration.foo).isEqualTo(foo);
    assertThat(configuration.x).isEqualTo(x);
    assertThat(configuration.nested.bar).isEqualTo(bar);
    assertThat(configuration.nested.y).isEqualTo(y);
  }

  @Test
  public void shouldCloseAllExportersOnClose() {
    // given
    final boolean[] closedExporters = new boolean[] {false, false};
    final ExporterStreamProcessor processor = createStreamProcessor(closedExporters.length);

    exporters.get(0).onClose(() -> closedExporters[0] = true);
    exporters.get(1).onClose(() -> closedExporters[1] = true);

    // when
    final StreamProcessorControl control = rule.initStreamProcessor(e -> processor);
    control.start();

    // then
    for (int i = 0; i < exporters.size(); i++) {
      assertThat(closedExporters[i]).isFalse();
    }

    // when
    control.close();

    // then
    for (int i = 0; i < exporters.size(); i++) {
      assertThat(closedExporters[i]).isTrue();
    }
  }

  @Test
  public void shouldRestartEachExporterFromCorrectPosition() {
    // given
    final ExporterStreamProcessor processor = createStreamProcessor(2);

    // when
    final StreamProcessorControl control = rule.initStreamProcessor(e -> processor);
    final long lowestPosition = writeEvent();
    final long highestPosition = writeEvent();

    control.blockAfterEvent(e -> e.getPosition() == highestPosition);
    control.start();
    TestUtil.waitUntil(control::isBlocked);
    control.unblock();

    // then
    TestUtil.waitUntil(() -> exporters.get(0).getExportedRecords().size() == 2);
    TestUtil.waitUntil(() -> exporters.get(1).getExportedRecords().size() == 2);

    // when
    exporters.get(0).getController().updateLastExportedRecordPosition(highestPosition);
    exporters.get(1).getController().updateLastExportedRecordPosition(lowestPosition);
    control.close();
    control.blockAfterEvent(e -> e.getPosition() == highestPosition);
    control.start();
    TestUtil.waitUntil(control::isBlocked);

    // then
    // should have reprocessed no events
    assertThat(exporters.get(0).getExportedRecords()).hasSize(2);
    assertThat(exporters.get(0).getExportedRecords().get(0).getPosition())
        .isEqualTo(lowestPosition);
    assertThat(exporters.get(0).getExportedRecords().get(1).getPosition())
        .isEqualTo(highestPosition);

    // should have reprocessed the last event twice
    assertThat(exporters.get(1).getExportedRecords()).hasSize(3);
    assertThat(exporters.get(1).getExportedRecords().get(0).getPosition())
        .isEqualTo(lowestPosition);
    assertThat(exporters.get(1).getExportedRecords().get(1).getPosition())
        .isEqualTo(highestPosition);
    assertThat(exporters.get(1).getExportedRecords().get(2).getPosition())
        .isEqualTo(highestPosition);
  }

  @Test
  public void shouldRecoverPositionsFromLogStream() {
    // given
    final List<ExporterDescriptor> descriptors = createMockedExporters(1);
    final ExporterStreamProcessor processor =
        new ExporterStreamProcessor(PARTITION_ID, descriptors);
    final ExporterStreamProcessorState state =
        (ExporterStreamProcessorState) processor.getStateController();

    // when
    final StreamProcessorControl control = rule.initStreamProcessor(e -> processor);
    final long lowestPosition = writeEvent();
    final long latestPosition = writeExporterEvent(descriptors.get(0).getId(), lowestPosition);

    control.blockAfterEvent(e -> e.getPosition() == latestPosition);
    control.start();
    TestUtil.waitUntil(control::isBlocked);

    // then
    assertThat(state.getPosition(descriptors.get(0).getId())).isEqualTo(lowestPosition);
  }

  @Test
  public void shouldRetryExportingOnException() {
    final ExporterStreamProcessor processor = createStreamProcessor(3);

    final AtomicLong failCount = new AtomicLong(3);
    exporters
        .get(1)
        .onExport(
            e -> {
              if (failCount.getAndDecrement() > 0) {
                throw new RuntimeException("Export failed (expected)");
              }
            });

    // when
    final StreamProcessorControl control = rule.initStreamProcessor(e -> processor);
    final long lowestPosition = writeEvent();
    final long highestPosition = writeEvent();

    control.blockAfterEvent(e -> e.getPosition() == highestPosition);
    control.start();
    TestUtil.waitUntil(control::isBlocked);

    // then
    for (final ControlledTestExporter exporter : exporters) {
      assertThat(exporter.getExportedRecords())
          .extracting("position")
          .containsExactly(lowestPosition, highestPosition);
    }
  }

  @Test
  public void shouldExecuteScheduledTask() throws InterruptedException {
    // given
    final ExporterStreamProcessor processor = createStreamProcessor(1);

    final CountDownLatch latch = new CountDownLatch(1);
    final Duration delay = Duration.ofSeconds(10);

    final ControlledTestExporter controlledTestExporter = exporters.get(0);
    controlledTestExporter.onExport(
        r -> controlledTestExporter.getController().scheduleTask(delay, latch::countDown));

    final long position = writeEvent();

    final StreamProcessorControl control = rule.initStreamProcessor(e -> processor);
    control.blockAfterEvent(e -> e.getPosition() == position);
    control.start();
    TestUtil.waitUntil(control::isBlocked);

    // when
    rule.getClock().addTime(delay.plusSeconds(10));
    final boolean wasExecuted = latch.await(1, TimeUnit.SECONDS);

    // then
    assertThat(wasExecuted).isTrue();
  }

  @Test
  public void shouldNotExportExporterRecords() {
    // given
    final List<ExporterDescriptor> descriptors = createMockedExporters(1);
    final ExporterStreamProcessor processor =
        new ExporterStreamProcessor(PARTITION_ID, descriptors);

    // when
    final StreamProcessorControl control = rule.initStreamProcessor(e -> processor);
    final long lowestPosition = writeEvent();
    final long latestPosition = writeExporterEvent(descriptors.get(0).getId(), lowestPosition);

    control.blockAfterEvent(e -> e.getPosition() == latestPosition);
    control.start();
    TestUtil.waitUntil(control::isBlocked);

    // then
    assertThat(exporters.get(0).getExportedRecords()).hasSize(1);
    assertThat(exporters.get(0).getExportedRecords().get(0).getPosition())
        .isEqualTo(lowestPosition);
  }

  @Test
  public void shouldExportIdEvent() {
    // given
    final int id = 1;

    final IdRecord record = new IdRecord().setId(id);
    final IdRecordValue expectedRecordValue = new IdRecordValueImpl(OBJECT_MAPPER, id);

    // then
    assertRecordExported(IdIntent.GENERATED, record, expectedRecordValue);
  }

  @Test
  public void shouldExportDeploymentEvent() {
    // given
    final String resourceName = "resource";
    final ResourceType resourceType = ResourceType.BPMN_XML;
    final DirectBuffer resource = wrapString("contents");
    final String bpmnProcessId = "testProcess";
    final long workflowKey = 123;
    final int workflowVersion = 12;

    final DeploymentRecord record = new DeploymentRecord();
    record
        .resources()
        .add()
        .setResourceName(wrapString(resourceName))
        .setResourceType(resourceType)
        .setResource(resource);
    record
        .deployedWorkflows()
        .add()
        .setBpmnProcessId(wrapString(bpmnProcessId))
        .setKey(workflowKey)
        .setResourceName(wrapString(resourceName))
        .setVersion(workflowVersion);

    final DeploymentRecordValue expectedRecordValue =
        new DeploymentRecordValueImpl(
            OBJECT_MAPPER,
            Collections.singletonList(
                new DeployedWorkflowImpl(
                    bpmnProcessId, resourceName, workflowKey, workflowVersion)),
            Collections.singletonList(
                new DeploymentResourceImpl(
                    BufferUtil.bufferAsArray(resource),
                    io.zeebe.exporter.record.value.deployment.ResourceType.BPMN_XML,
                    resourceName)));

    // then
    assertRecordExported(DeploymentIntent.CREATE, record, expectedRecordValue);
  }

  @Test
  public void shouldExportIncidentRecord() {
    // given
    final long activityInstanceKey = 34;
    final long workflowInstanceKey = 10;
    final long failureEventPosition = 12;
    final String activityId = "activity";
    final String bpmnProcessId = "process";
    final String errorMessage = "error";
    final ErrorType errorType = ErrorType.IO_MAPPING_ERROR;
    final long jobKey = 123;

    final IncidentRecord record =
        new IncidentRecord()
            .setActivityInstanceKey(activityInstanceKey)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setFailureEventPosition(failureEventPosition)
            .setActivityId(wrapString(activityId))
            .setBpmnProcessId(wrapString(bpmnProcessId))
            .setErrorMessage(errorMessage)
            .setErrorType(errorType)
            .setJobKey(jobKey)
            .setPayload(PAYLOAD_MSGPACK);

    final IncidentRecordValue recordValue =
        new IncidentRecordValueImpl(
            OBJECT_MAPPER,
            PAYLOAD_JSON,
            errorType.name(),
            errorMessage,
            bpmnProcessId,
            activityId,
            workflowInstanceKey,
            activityInstanceKey,
            jobKey);

    // then
    assertRecordExported(IncidentIntent.CREATED, record, recordValue);
  }

  @Test
  public void shouldExportJobRecord() {
    // given
    final String worker = "myWorker";
    final String type = "myType";
    final int retries = 12;
    final int deadline = 13;

    final String bpmnProcessId = "test-process";
    final int workflowKey = 13;
    final int workflowDefinitionVersion = 12;
    final int workflowInstanceKey = 1234;
    final String activityId = "activity";
    final int activityInstanceKey = 123;

    final JobRecord record =
        new JobRecord()
            .setWorker(wrapString(worker))
            .setType(wrapString(type))
            .setPayload(PAYLOAD_MSGPACK)
            .setRetries(retries)
            .setDeadline(deadline);
    record
        .headers()
        .setBpmnProcessId(wrapString(bpmnProcessId))
        .setWorkflowKey(workflowKey)
        .setWorkflowDefinitionVersion(workflowDefinitionVersion)
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setActivityId(wrapString(activityId))
        .setActivityInstanceKey(activityInstanceKey);

    record.setCustomHeaders(CUSTOM_HEADERS_MSGPACK);

    final JobRecordValue recordValue =
        new JobRecordValueImpl(
            OBJECT_MAPPER,
            PAYLOAD_JSON,
            type,
            worker,
            Instant.ofEpochMilli(deadline),
            new HeadersImpl(
                bpmnProcessId,
                activityId,
                activityInstanceKey,
                workflowInstanceKey,
                workflowKey,
                workflowDefinitionVersion),
            CUSTOM_HEADERS,
            retries);

    // then
    assertRecordExported(JobIntent.CREATED, record, recordValue);
  }

  @Test
  public void shouldExportMessageRecord() {
    // given
    final String correlationKey = "test-key";
    final String messageName = "test-message";
    final long timeToLive = 12;
    final String messageId = "test-id";

    final MessageRecord record =
        new MessageRecord()
            .setCorrelationKey(wrapString(correlationKey))
            .setName(wrapString(messageName))
            .setPayload(PAYLOAD_MSGPACK)
            .setTimeToLive(timeToLive)
            .setMessageId(wrapString(messageId));

    final MessageRecordValue recordValue =
        new MessageRecordValueImpl(
            OBJECT_MAPPER, PAYLOAD_JSON, messageName, messageId, correlationKey, timeToLive);

    // then
    assertRecordExported(MessageIntent.PUBLISHED, record, recordValue);
  }

  @Test
  public void shouldExportMessageSubscriptionRecord() {
    // given
    final long activityInstanceKey = 1L;
    final String messageName = "name";
    final long workflowInstanceKey = 1L;
    final int workflowInstancePartitionId = 1;
    final String correlationKey = "key";

    final MessageSubscriptionRecord record =
        new MessageSubscriptionRecord()
            .setActivityInstanceKey(activityInstanceKey)
            .setMessageName(wrapString(messageName))
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setWorkflowInstancePartitionId(workflowInstancePartitionId)
            .setCorrelationKey(wrapString(correlationKey));

    final MessageSubscriptionRecordValue recordValue =
        new MessageSubscriptionRecordValueImpl(
            OBJECT_MAPPER,
            messageName,
            correlationKey,
            workflowInstancePartitionId,
            workflowInstanceKey,
            activityInstanceKey);

    // then
    assertRecordExported(MessageSubscriptionIntent.CORRELATE, record, recordValue);
  }

  @Test
  public void shouldExportRaftRecord() {
    // given
    final List<Integer> nodeIds = IntStream.of(4).boxed().collect(Collectors.toList());

    final RaftConfigurationEvent record = new RaftConfigurationEvent();
    nodeIds.forEach(i -> record.members().add().setNodeId(i));

    final RaftRecordValue recordValue =
        new RaftRecordValueImpl(
            OBJECT_MAPPER, nodeIds.stream().map(RaftMemberImpl::new).collect(Collectors.toList()));

    // then
    assertRecordExported(RaftIntent.MEMBER_ADDED, record, recordValue);
  }

  @Test
  public void shouldExportTopicRecord() {
    // given
    final String topic = "test-topic";
    final int partitions = 12;
    final int replicationFactor = 34;
    final List<Integer> partitionIds =
        IntStream.of(partitions).boxed().collect(Collectors.toList());

    final TopicRecord record =
        new TopicRecord()
            .setName(wrapString(topic))
            .setPartitions(partitions)
            .setReplicationFactor(replicationFactor);
    partitionIds.forEach(i -> record.getPartitionIds().add().setValue(i));

    final TopicRecordValue recordValue =
        new TopicRecordValueImpl(OBJECT_MAPPER, topic, partitionIds, partitions, replicationFactor);

    // then
    assertRecordExported(TopicIntent.CREATED, record, recordValue);
  }

  @Test
  public void shouldExportWorkflowInstanceRecord() {
    // given
    final String bpmnProcessId = "test-process";
    final int workflowKey = 13;
    final int version = 12;
    final int workflowInstanceKey = 1234;
    final String activityId = "activity";
    final int scopeInstanceKey = 123;

    final WorkflowInstanceRecord record =
        new WorkflowInstanceRecord()
            .setActivityId(activityId)
            .setPayload(PAYLOAD_MSGPACK)
            .setBpmnProcessId(wrapString(bpmnProcessId))
            .setVersion(version)
            .setWorkflowKey(workflowKey)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setScopeInstanceKey(scopeInstanceKey);

    final WorkflowInstanceRecordValue recordValue =
        new WorkflowInstanceRecordValueImpl(
            OBJECT_MAPPER,
            PAYLOAD_JSON,
            bpmnProcessId,
            activityId,
            version,
            workflowKey,
            workflowInstanceKey,
            scopeInstanceKey);

    // then
    assertRecordExported(WorkflowInstanceIntent.CREATED, record, recordValue);
  }

  @Test
  public void shouldExportWorkflowInstanceSubscriptionRecord() {
    // given
    final long activityInstanceKey = 123;
    final String messageName = "test-message";
    final int subscriptionPartitionId = 2;
    final long workflowInstanceKey = 1345;

    final WorkflowInstanceSubscriptionRecord record =
        new WorkflowInstanceSubscriptionRecord()
            .setActivityInstanceKey(activityInstanceKey)
            .setMessageName(wrapString(messageName))
            .setSubscriptionPartitionId(subscriptionPartitionId)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setPayload(PAYLOAD_MSGPACK);

    final WorkflowInstanceSubscriptionRecordValue recordValue =
        new WorkflowInstanceSubscriptionRecordValueImpl(
            OBJECT_MAPPER, PAYLOAD_JSON, messageName, workflowInstanceKey, activityInstanceKey);

    // then
    assertRecordExported(WorkflowInstanceSubscriptionIntent.OPENED, record, recordValue);
  }

  private ExporterStreamProcessor createStreamProcessor(final int count) {
    return new ExporterStreamProcessor(PARTITION_ID, createMockedExporters(count));
  }

  private List<ExporterDescriptor> createMockedExporters(final int count) {
    return createMockedExporters(count, new Map[0]);
  }

  private List<ExporterDescriptor> createMockedExporters(final Map... arguments) {
    return createMockedExporters(arguments.length, arguments);
  }

  @SuppressWarnings("unchecked")
  private List<ExporterDescriptor> createMockedExporters(final int count, final Map[] arguments) {
    final List<ExporterDescriptor> descriptors = new ArrayList<>(count);
    exporters = new ArrayList<>(count);

    for (int i = 0; i < count; i++) {
      final Map args = arguments.length > 0 ? arguments[i] : null;
      final ControlledTestExporter exporter = spy(new ControlledTestExporter());
      final ExporterDescriptor descriptor =
          spy(new ExporterDescriptor(String.valueOf(i), exporter.getClass(), args));
      doAnswer(c -> exporter).when(descriptor).newInstance();

      exporters.add(exporter);
      descriptors.add(descriptor);
    }

    return descriptors;
  }

  private long writeEvent() {
    final IdRecord event = new IdRecord();
    event.setId(0);
    return rule.writeEvent(IdIntent.GENERATED, event);
  }

  private long writeExporterEvent(final String id, final long position) {
    final ExporterRecord event = new ExporterRecord();
    final ExporterPosition exporterPosition = event.getPositions().add();
    exporterPosition.setId(id);
    exporterPosition.setPosition(position);

    return rule.writeEvent(ExporterIntent.EXPORTED, event);
  }

  private Map<String, Object> newConfig(final String... pairs) {
    final Map<String, Object> config = new HashMap<>();

    for (int i = 0; i < pairs.length; i += 2) {
      config.put(pairs[i], pairs[i + 1]);
    }

    return config;
  }

  private void assertRecordExported(
      final Intent intent, final UnpackedObject record, final RecordValue expectedRecordValue) {
    // setup stream processor
    final StreamProcessorControl control = rule.initStreamProcessor(e -> createStreamProcessor(1));

    // write event
    final long position = rule.writeEvent(intent, record);

    // wait for event
    control.blockAfterEvent(e -> e.getPosition() == position);
    control.start();
    TestUtil.waitUntil(control::isBlocked);

    // assert exported record
    final List<Record> exportedRecords = exporters.get(0).getExportedRecords();
    assertThat(exportedRecords).hasSize(1);

    final Record exportedRecord = exportedRecords.get(0);
    final LoggedEvent loggedEvent = rule.events().withPosition(position);
    assertMetadata(exportedRecord, loggedEvent);
    assertThat(exportedRecord.getValue()).isEqualTo(expectedRecordValue);
  }

  private void assertMetadata(final Record actualRecord, final LoggedEvent expectedLoggedEvent) {
    assertThat(actualRecord.getPosition()).isEqualTo(expectedLoggedEvent.getPosition());
    assertThat(actualRecord.getRaftTerm()).isEqualTo(expectedLoggedEvent.getRaftTerm());
    assertThat(actualRecord.getSourceRecordPosition())
        .isEqualTo(expectedLoggedEvent.getSourceEventPosition());
    assertThat(actualRecord.getProducerId()).isEqualTo(expectedLoggedEvent.getProducerId());
    assertThat(actualRecord.getKey()).isEqualTo(expectedLoggedEvent.getKey());
    assertThat(actualRecord.getTimestamp())
        .isEqualTo(Instant.ofEpochMilli(expectedLoggedEvent.getTimestamp()));

    final RecordMetadata actualMetadata = actualRecord.getMetadata();
    final io.zeebe.protocol.impl.RecordMetadata expectedMetadata =
        new io.zeebe.protocol.impl.RecordMetadata();
    expectedLoggedEvent.readMetadata(expectedMetadata);

    assertThat(actualMetadata.getIntent()).isEqualTo(expectedMetadata.getIntent());
    assertThat(actualMetadata.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(actualMetadata.getRecordType()).isEqualTo(expectedMetadata.getRecordType());
    assertThat(actualMetadata.getRejectionType()).isEqualTo(expectedMetadata.getRejectionType());
    assertThat(actualMetadata.getRejectionReason())
        .isEqualTo(BufferUtil.bufferAsString(expectedMetadata.getRejectionReason()));
    assertThat(actualMetadata.getValueType()).isEqualTo(expectedMetadata.getValueType());
  }
}
