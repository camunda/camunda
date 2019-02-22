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

import static io.zeebe.exporter.record.Assertions.assertThat;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.broker.exporter.record.value.IncidentRecordValueImpl;
import io.zeebe.broker.exporter.record.value.JobBatchRecordValueImpl;
import io.zeebe.broker.exporter.record.value.JobRecordValueImpl;
import io.zeebe.broker.exporter.record.value.MessageSubscriptionRecordValueImpl;
import io.zeebe.broker.exporter.record.value.VariableRecordValueImpl;
import io.zeebe.broker.exporter.record.value.WorkflowInstanceSubscriptionRecordValueImpl;
import io.zeebe.broker.exporter.record.value.deployment.DeployedWorkflowImpl;
import io.zeebe.broker.exporter.record.value.deployment.DeploymentResourceImpl;
import io.zeebe.broker.exporter.record.value.job.HeadersImpl;
import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.broker.exporter.stream.ExporterRecord.ExporterPosition;
import io.zeebe.broker.exporter.util.ControlledTestExporter;
import io.zeebe.broker.exporter.util.PojoConfigurationExporter;
import io.zeebe.broker.exporter.util.PojoConfigurationExporter.PojoExporterConfiguration;
import io.zeebe.broker.logstreams.state.DefaultZeebeDbFactory;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.util.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.db.ZeebeDb;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordValue;
import io.zeebe.exporter.record.value.DeploymentRecordValue;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.exporter.record.value.MessageRecordValue;
import io.zeebe.exporter.record.value.MessageSubscriptionRecordValue;
import io.zeebe.exporter.record.value.VariableRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.impl.record.value.incident.ErrorType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.ExporterIntent;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.VariableIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

public class ExporterStreamProcessorTest {
  private static final int PARTITION_ID = 1;
  private static final ExporterObjectMapper OBJECT_MAPPER = new ExporterObjectMapper();
  private static final Map<String, Object> PAYLOAD = Collections.singletonMap("foo", "bar");
  private static final String PAYLOAD_JSON = OBJECT_MAPPER.toJson(PAYLOAD);
  private static final DirectBuffer PAYLOAD_MSGPACK =
      new UnsafeBuffer(OBJECT_MAPPER.toMsgpack(PAYLOAD));
  private static final Map<String, Object> CUSTOM_HEADERS =
      Collections.singletonMap("workerVersion", 42);
  private static final DirectBuffer CUSTOM_HEADERS_MSGPACK =
      new UnsafeBuffer(OBJECT_MAPPER.toMsgpack(CUSTOM_HEADERS));

  @Rule
  public StreamProcessorRule rule =
      new StreamProcessorRule(
          PARTITION_ID, DefaultZeebeDbFactory.defaultFactory(ExporterColumnFamilies.class));

  private List<ControlledTestExporter> exporters;
  private ExporterStreamProcessorState state;

  @Test
  public void shouldConfigureAllExportersProperlyOnStart() throws InterruptedException {
    // given
    final Map[] arguments = new Map[] {newConfig("foo", "bar"), newConfig("bar", "foo")};
    final List<ExporterDescriptor> descriptors = createMockedExporters(arguments);

    final CountDownLatch latch = new CountDownLatch(exporters.size());
    for (ControlledTestExporter exporter : exporters) {
      exporter.onOpen(c -> latch.countDown());
    }

    // when
    final StreamProcessorControl control =
        rule.initStreamProcessor(
            (db) -> new ExporterStreamProcessor(db, PARTITION_ID, descriptors));
    control.start();
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

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

    rule.runStreamProcessor(
        (db) ->
            new ExporterStreamProcessor(db, PARTITION_ID, Collections.singletonList(descriptor)));

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

    // when
    final StreamProcessorControl control =
        rule.runStreamProcessor((db) -> createStreamProcessor(db, closedExporters.length));
    exporters.get(0).onClose(() -> closedExporters[0] = true);
    exporters.get(1).onClose(() -> closedExporters[1] = true);

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
    final StreamProcessorControl control =
        rule.runStreamProcessor((db) -> createStreamProcessor(db, 2));
    final AtomicLong atomicLong = new AtomicLong();
    control.blockAfterEvent(e -> atomicLong.incrementAndGet() == 2);

    // when
    final long lowestPosition = writeEvent();
    final long highestPosition = writeEvent();
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
    assertThat(exporters.get(0).getExportedRecords()).hasSize(0);

    // should have reprocessed the last event twice
    assertThat(exporters.get(1).getExportedRecords()).hasSize(1);
    assertThat(exporters.get(1).getExportedRecords().get(0).getPosition())
        .isEqualTo(highestPosition);
  }

  @Test
  public void shouldRecoverPositionsFromLogStream() {
    // given
    final List<ExporterDescriptor> descriptors = createMockedExporters(1);

    // when
    final StreamProcessorControl control =
        rule.initStreamProcessor(
            (db) -> {
              final ExporterStreamProcessor processor =
                  new ExporterStreamProcessor(db, PARTITION_ID, descriptors);
              state = processor.getState();
              return processor;
            });

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
    final StreamProcessorControl control =
        rule.runStreamProcessor((db) -> createStreamProcessor(db, 3));

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
    final long lowestPosition = writeEvent();
    final long highestPosition = writeEvent();

    control.blockAfterEvent(e -> e.getPosition() == highestPosition);
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
    final StreamProcessorControl control =
        rule.runStreamProcessor((db) -> createStreamProcessor(db, 1));

    final CountDownLatch latch = new CountDownLatch(1);
    final Duration delay = Duration.ofSeconds(10);

    final ControlledTestExporter controlledTestExporter = exporters.get(0);
    controlledTestExporter.onExport(
        r -> controlledTestExporter.getController().scheduleTask(delay, latch::countDown));

    control.blockAfterEvent(e -> true);
    writeEvent();
    TestUtil.waitUntil(control::isBlocked);

    // when
    rule.getClock().addTime(delay.plusSeconds(20));
    final boolean wasExecuted = latch.await(1, TimeUnit.SECONDS);

    // then
    assertThat(wasExecuted).isTrue();
  }

  @Test
  public void shouldNotExportExporterRecords() {
    // given
    final List<ExporterDescriptor> descriptors = createMockedExporters(1);

    // when
    final StreamProcessorControl control =
        rule.initStreamProcessor(
            (db) -> new ExporterStreamProcessor(db, PARTITION_ID, descriptors));
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
        .workflows()
        .add()
        .setBpmnProcessId(wrapString(bpmnProcessId))
        .setKey(workflowKey)
        .setResourceName(wrapString(resourceName))
        .setVersion(workflowVersion);

    final DeploymentRecordValue expectedRecordValue =
        new io.zeebe.broker.exporter.record.value.DeploymentRecordValueImpl(
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
    final long elementInstanceKey = 34;
    final long workflowInstanceKey = 10;
    final String elementId = "activity";
    final String bpmnProcessId = "process";
    final String errorMessage = "error";
    final ErrorType errorType = ErrorType.IO_MAPPING_ERROR;
    final long jobKey = 123;

    final IncidentRecord record =
        new IncidentRecord()
            .setElementInstanceKey(elementInstanceKey)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setElementId(wrapString(elementId))
            .setBpmnProcessId(wrapString(bpmnProcessId))
            .setErrorMessage(errorMessage)
            .setErrorType(errorType)
            .setJobKey(jobKey)
            .setVariableScopeKey(elementInstanceKey);

    final IncidentRecordValue recordValue =
        new IncidentRecordValueImpl(
            OBJECT_MAPPER,
            errorType.name(),
            errorMessage,
            bpmnProcessId,
            elementId,
            workflowInstanceKey,
            elementInstanceKey,
            jobKey,
            elementInstanceKey);

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
    final String elementId = "activity";
    final int activityInstanceKey = 123;

    final JobRecord record =
        new JobRecord()
            .setWorker(wrapString(worker))
            .setType(wrapString(type))
            .setPayload(PAYLOAD_MSGPACK)
            .setRetries(retries)
            .setDeadline(deadline)
            .setErrorMessage("failed message");
    record
        .getHeaders()
        .setBpmnProcessId(wrapString(bpmnProcessId))
        .setWorkflowKey(workflowKey)
        .setWorkflowDefinitionVersion(workflowDefinitionVersion)
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setElementId(wrapString(elementId))
        .setElementInstanceKey(activityInstanceKey);

    record.setCustomHeaders(CUSTOM_HEADERS_MSGPACK);

    final JobRecordValue recordValue =
        new io.zeebe.broker.exporter.record.value.JobRecordValueImpl(
            OBJECT_MAPPER,
            PAYLOAD_JSON,
            type,
            worker,
            Instant.ofEpochMilli(deadline),
            new HeadersImpl(
                bpmnProcessId,
                elementId,
                activityInstanceKey,
                workflowInstanceKey,
                workflowKey,
                workflowDefinitionVersion),
            CUSTOM_HEADERS,
            retries,
            "failed message");

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
        new io.zeebe.broker.exporter.record.value.MessageRecordValueImpl(
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
    final String correlationKey = "key";

    final MessageSubscriptionRecord record =
        new MessageSubscriptionRecord()
            .setElementInstanceKey(activityInstanceKey)
            .setMessageName(wrapString(messageName))
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setCorrelationKey(wrapString(correlationKey));

    final MessageSubscriptionRecordValue recordValue =
        new MessageSubscriptionRecordValueImpl(
            OBJECT_MAPPER, messageName, correlationKey, workflowInstanceKey, activityInstanceKey);

    // then
    assertRecordExported(MessageSubscriptionIntent.CORRELATE, record, recordValue);
  }

  @Test
  public void shouldExportWorkflowInstanceRecord() {
    // given
    final String bpmnProcessId = "test-process";
    final int workflowKey = 13;
    final int version = 12;
    final int workflowInstanceKey = 1234;
    final String elementId = "activity";
    final int flowScopeKey = 123;
    final BpmnElementType bpmnElementType = BpmnElementType.SERVICE_TASK;

    final WorkflowInstanceRecord record =
        new WorkflowInstanceRecord()
            .setElementId(elementId)
            .setBpmnElementType(bpmnElementType)
            .setPayload(PAYLOAD_MSGPACK)
            .setBpmnProcessId(wrapString(bpmnProcessId))
            .setVersion(version)
            .setWorkflowKey(workflowKey)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setFlowScopeKey(flowScopeKey);

    final WorkflowInstanceRecordValue recordValue =
        new io.zeebe.broker.exporter.record.value.WorkflowInstanceRecordValueImpl(
            OBJECT_MAPPER,
            PAYLOAD_JSON,
            bpmnProcessId,
            elementId,
            version,
            workflowKey,
            workflowInstanceKey,
            flowScopeKey,
            bpmnElementType);

    // then
    assertRecordExported(WorkflowInstanceIntent.ELEMENT_ACTIVATING, record, recordValue);
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
            .setElementInstanceKey(activityInstanceKey)
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

  @Test
  public void shouldExportJobBatchRecord() {
    // given
    final int amount = 1;
    final long timeout = 2L;
    final String type = "type";
    final String worker = "worker";

    final JobBatchRecord record =
        new JobBatchRecord()
            .setAmount(amount)
            .setTimeout(timeout)
            .setType(type)
            .setWorker(worker)
            .setTruncated(true);

    record.jobKeys().add().setValue(3L);
    final JobRecord jobRecord = record.jobs().add();

    final String bpmnProcessId = "test-process";
    final int workflowKey = 13;
    final int workflowDefinitionVersion = 12;
    final int workflowInstanceKey = 1234;
    final String activityId = "activity";
    final int activityInstanceKey = 123;

    jobRecord
        .setWorker(wrapString(worker))
        .setType(wrapString(type))
        .setPayload(PAYLOAD_MSGPACK)
        .setRetries(3)
        .setErrorMessage("failed message")
        .setDeadline(1000L);

    jobRecord
        .getHeaders()
        .setBpmnProcessId(wrapString(bpmnProcessId))
        .setWorkflowKey(workflowKey)
        .setWorkflowDefinitionVersion(workflowDefinitionVersion)
        .setWorkflowInstanceKey(workflowInstanceKey)
        .setElementId(wrapString(activityId))
        .setElementInstanceKey(activityInstanceKey);

    final JobRecordValueImpl jobRecordValue =
        new JobRecordValueImpl(
            OBJECT_MAPPER,
            PAYLOAD_JSON,
            type,
            worker,
            Instant.ofEpochMilli(1000L),
            new HeadersImpl(
                bpmnProcessId,
                activityId,
                activityInstanceKey,
                workflowInstanceKey,
                workflowKey,
                workflowDefinitionVersion),
            Collections.EMPTY_MAP,
            3,
            "failed message");

    final JobBatchRecordValueImpl recordValue =
        new JobBatchRecordValueImpl(
            OBJECT_MAPPER,
            type,
            worker,
            Duration.ofMillis(timeout),
            amount,
            Arrays.asList(3L),
            Arrays.asList(jobRecordValue),
            record.getTruncated());

    // then
    assertRecordExported(JobBatchIntent.ACTIVATED, record, recordValue);
  }

  @Test
  public void shouldExportVariableRecord() {
    // given
    final String name = "x";
    final String value = "1";
    final long scopeKey = 3;
    final long workflowInstanceKey = 2;

    final VariableRecord record =
        new VariableRecord()
            .setName(wrapString(name))
            .setValue(MsgPackUtil.asMsgPack(value))
            .setScopeKey(scopeKey)
            .setWorkflowInstanceKey(workflowInstanceKey);

    final VariableRecordValue recordValue =
        new VariableRecordValueImpl(OBJECT_MAPPER, name, value, scopeKey, workflowInstanceKey);

    // then
    assertRecordExported(VariableIntent.CREATED, record, recordValue);
  }

  @Test
  public void shouldUpdateLastExportedPositionOnClose() {
    // given
    final StreamProcessorControl control =
        rule.initStreamProcessor((db) -> createStreamProcessor(db, 1));
    control.start();

    final long firstPosition = writeEvent();
    final ControlledTestExporter firstExporter = exporters.get(0);
    TestUtil.waitUntil(() -> firstExporter.getExportedRecords().size() == 1);
    firstExporter.onClose(
        () -> firstExporter.getController().updateLastExportedRecordPosition(firstPosition));
    control.close();

    // when
    final long secondPosition = writeEvent();
    control.blockAfterEvent(e -> e.getPosition() == secondPosition);
    control.start();
    TestUtil.waitUntil(control::isBlocked);

    // then
    final List<Record> records = firstExporter.getExportedRecords();
    assertThat(records).hasSize(1);
    assertThat(records.get(0).getPosition()).isEqualTo(firstPosition);

    final ControlledTestExporter secondExporter = exporters.get(0);
    assertThat(firstExporter).isNotEqualTo(secondExporter);

    final List<Record> secondRecords = secondExporter.getExportedRecords();
    assertThat(secondRecords).hasSize(1);
    assertThat(secondRecords.get(0).getPosition()).isEqualTo(secondPosition);
  }

  private ExporterStreamProcessor createStreamProcessor(ZeebeDb db, final int count) {
    return new ExporterStreamProcessor(db, PARTITION_ID, createMockedExporters(count));
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
    final DeploymentRecord event = new DeploymentRecord();
    return rule.writeEvent(DeploymentIntent.CREATED, event);
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
    final StreamProcessorControl control =
        rule.initStreamProcessor((db) -> createStreamProcessor(db, 1));

    // write event
    final long position = rule.writeEvent(intent, record);

    // wait for event
    control.blockAfterEvent(e -> e.getPosition() == position);
    control.start();
    TestUtil.waitUntil(control::isBlocked);

    // assert exported record
    final List<Record> exportedRecords = exporters.get(0).getExportedRecords();
    assertThat(exportedRecords).hasSize(1);

    final Record actualRecord = exportedRecords.get(0);
    final LoggedEvent loggedEvent = rule.events().withPosition(position);
    final RecordMetadata metadata = new RecordMetadata();

    assertThat(actualRecord)
        .hasPosition(loggedEvent.getPosition())
        .hasRaftTerm(loggedEvent.getRaftTerm())
        .hasSourceRecordPosition(loggedEvent.getSourceEventPosition())
        .hasProducerId(loggedEvent.getProducerId())
        .hasKey(loggedEvent.getKey())
        .hasTimestamp(Instant.ofEpochMilli(loggedEvent.getTimestamp()));

    loggedEvent.readMetadata(metadata);

    assertThat(actualRecord.getMetadata())
        .hasIntent(metadata.getIntent())
        .hasPartitionId(PARTITION_ID)
        .hasRecordType(metadata.getRecordType())
        .hasRejectionType(metadata.getRejectionType())
        .hasRejectionReason(bufferAsString(metadata.getRejectionReason()))
        .hasValueType(metadata.getValueType());

    assertThat(actualRecord).hasValue(expectedRecordValue);
  }
}
