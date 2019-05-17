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

import static io.zeebe.exporter.api.record.Assertions.assertThat;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapArray;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.broker.exporter.record.value.DeploymentRecordValueImpl;
import io.zeebe.broker.exporter.record.value.IncidentRecordValueImpl;
import io.zeebe.broker.exporter.record.value.JobBatchRecordValueImpl;
import io.zeebe.broker.exporter.record.value.JobRecordValueImpl;
import io.zeebe.broker.exporter.record.value.MessageRecordValueImpl;
import io.zeebe.broker.exporter.record.value.MessageSubscriptionRecordValueImpl;
import io.zeebe.broker.exporter.record.value.VariableDocumentRecordValueImpl;
import io.zeebe.broker.exporter.record.value.VariableRecordValueImpl;
import io.zeebe.broker.exporter.record.value.WorkflowInstanceCreationRecordValueImpl;
import io.zeebe.broker.exporter.record.value.WorkflowInstanceRecordValueImpl;
import io.zeebe.broker.exporter.record.value.WorkflowInstanceSubscriptionRecordValueImpl;
import io.zeebe.broker.exporter.record.value.deployment.DeployedWorkflowImpl;
import io.zeebe.broker.exporter.record.value.deployment.DeploymentResourceImpl;
import io.zeebe.broker.exporter.record.value.job.HeadersImpl;
import io.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.zeebe.broker.exporter.util.ControlledTestExporter;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordValue;
import io.zeebe.exporter.api.record.value.DeploymentRecordValue;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.MessageRecordValue;
import io.zeebe.exporter.api.record.value.MessageSubscriptionRecordValue;
import io.zeebe.exporter.api.record.value.VariableDocumentRecordValue;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceCreationRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import io.zeebe.protocol.intent.VariableIntent;
import io.zeebe.protocol.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

public class ExporterRecordTest {

  private static final int PARTITION_ID = 1;
  private static final ExporterObjectMapper OBJECT_MAPPER = new ExporterObjectMapper();

  private static final Map<String, Object> VARIABLES = Collections.singletonMap("foo", "bar");
  private static final String VARIABLES_JSON = OBJECT_MAPPER.toJson(VARIABLES);
  private static final DirectBuffer VARIABLES_MSGPACK =
      new UnsafeBuffer(OBJECT_MAPPER.toMsgpack(VARIABLES));

  @Rule public ExporterRule rule = new ExporterRule(PARTITION_ID);

  private ControlledTestExporter exporter;

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
        new DeploymentRecordValueImpl(
            OBJECT_MAPPER,
            Collections.singletonList(
                new DeployedWorkflowImpl(
                    bpmnProcessId, resourceName, workflowKey, workflowVersion)),
            Collections.singletonList(
                new DeploymentResourceImpl(
                    BufferUtil.bufferAsArray(resource),
                    io.zeebe.exporter.api.record.value.deployment.ResourceType.BPMN_XML,
                    resourceName)));

    // then
    assertRecordExported(DeploymentIntent.CREATE, record, expectedRecordValue);
  }

  @Test
  public void shouldExportIncidentRecord() {
    // given
    final long elementInstanceKey = 34;
    final long workflowKey = 134;
    final long workflowInstanceKey = 10;
    final String elementId = "activity";
    final String bpmnProcessId = "process";
    final String errorMessage = "error";
    final ErrorType errorType = ErrorType.IO_MAPPING_ERROR;
    final long jobKey = 123;

    final IncidentRecord record =
        new IncidentRecord()
            .setElementInstanceKey(elementInstanceKey)
            .setWorkflowKey(workflowKey)
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
            workflowKey,
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

    final Map<String, Object> customHeaders = Collections.singletonMap("workerVersion", 42);

    final JobRecord record =
        new JobRecord()
            .setWorker(wrapString(worker))
            .setType(wrapString(type))
            .setVariables(VARIABLES_MSGPACK)
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

    record.setCustomHeaders(wrapArray(OBJECT_MAPPER.toMsgpack(customHeaders)));

    final JobRecordValue recordValue =
        new JobRecordValueImpl(
            OBJECT_MAPPER,
            VARIABLES_JSON,
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
            customHeaders,
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
            .setVariables(VARIABLES_MSGPACK)
            .setTimeToLive(timeToLive)
            .setMessageId(wrapString(messageId));

    final MessageRecordValue recordValue =
        new MessageRecordValueImpl(
            OBJECT_MAPPER, VARIABLES_JSON, messageName, messageId, correlationKey, timeToLive);

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
    final long messageKey = 1L;

    final MessageSubscriptionRecord record =
        new MessageSubscriptionRecord()
            .setElementInstanceKey(activityInstanceKey)
            .setMessageKey(messageKey)
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
            .setBpmnProcessId(wrapString(bpmnProcessId))
            .setVersion(version)
            .setWorkflowKey(workflowKey)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setFlowScopeKey(flowScopeKey);

    final WorkflowInstanceRecordValue recordValue =
        new WorkflowInstanceRecordValueImpl(
            OBJECT_MAPPER,
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
    final int messageKey = 3;
    final long workflowInstanceKey = 1345;

    final WorkflowInstanceSubscriptionRecord record =
        new WorkflowInstanceSubscriptionRecord()
            .setElementInstanceKey(activityInstanceKey)
            .setMessageName(wrapString(messageName))
            .setMessageKey(messageKey)
            .setSubscriptionPartitionId(subscriptionPartitionId)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setVariables(VARIABLES_MSGPACK);

    final WorkflowInstanceSubscriptionRecordValue recordValue =
        new WorkflowInstanceSubscriptionRecordValueImpl(
            OBJECT_MAPPER, VARIABLES_JSON, messageName, workflowInstanceKey, activityInstanceKey);

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
            .setMaxJobsToActivate(amount)
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
        .setVariables(VARIABLES_MSGPACK)
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
            VARIABLES_JSON,
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
            Collections.emptyMap(),
            3,
            "failed message");

    final JobBatchRecordValueImpl recordValue =
        new JobBatchRecordValueImpl(
            OBJECT_MAPPER,
            type,
            worker,
            Duration.ofMillis(timeout),
            amount,
            Collections.singletonList(3L),
            Collections.singletonList(jobRecordValue),
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
    final long workflowKey = 4;

    final VariableRecord record =
        new VariableRecord()
            .setName(wrapString(name))
            .setValue(MsgPackUtil.asMsgPack(value))
            .setScopeKey(scopeKey)
            .setWorkflowInstanceKey(workflowInstanceKey)
            .setWorkflowKey(workflowKey);

    final VariableRecordValue recordValue =
        new VariableRecordValueImpl(
            OBJECT_MAPPER, name, value, scopeKey, workflowInstanceKey, workflowKey);

    // then
    assertRecordExported(VariableIntent.CREATED, record, recordValue);
  }

  @Test
  public void shouldExportVariableDocumentRecord() {
    // given
    final long scopeKey = 1L;
    final VariableDocumentUpdateSemantic updateSemantics = VariableDocumentUpdateSemantic.LOCAL;
    final Map<String, Object> document = Maps.of(entry("foo", "bar"), entry("baz", "boz"));

    final VariableDocumentRecord record =
        new VariableDocumentRecord()
            .setScopeKey(scopeKey)
            .setUpdateSemantics(updateSemantics)
            .setDocument(MsgPackUtil.asMsgPack(document));

    final VariableDocumentRecordValue recordValue =
        new VariableDocumentRecordValueImpl(OBJECT_MAPPER, scopeKey, updateSemantics, document);

    // then
    assertRecordExported(VariableDocumentIntent.UPDATED, record, recordValue);
  }

  @Test
  public void shouldExportWorkflowInstanceCreationRecord() {
    // given
    final String processId = "process";
    final long key = 1L;
    final int version = 1;
    final long instanceKey = 2L;
    final Map<String, Object> variables = Maps.of(entry("foo", "bar"), entry("baz", "boz"));

    final WorkflowInstanceCreationRecord record =
        new WorkflowInstanceCreationRecord()
            .setBpmnProcessId(processId)
            .setKey(key)
            .setVersion(version)
            .setVariables(MsgPackUtil.asMsgPack(variables))
            .setInstanceKey(instanceKey);

    final WorkflowInstanceCreationRecordValue recordValue =
        new WorkflowInstanceCreationRecordValueImpl(
            OBJECT_MAPPER, processId, version, key, instanceKey, variables);

    // then
    assertRecordExported(WorkflowInstanceCreationIntent.CREATED, record, recordValue);
  }

  private void assertRecordExported(
      final Intent intent, final UnpackedObject record, final RecordValue expectedRecordValue) {
    // setup stream processor
    final List<ExporterDescriptor> exporterDescriptors =
        Collections.singletonList(createMockedExporter());
    rule.startExporterDirector(exporterDescriptors);

    // write event
    final long position = rule.writeEvent(intent, record);

    // wait for event
    final List<Record> exportedRecords = exporter.getExportedRecords();
    waitUntil(() -> exportedRecords.size() >= 1);

    // assert exported record
    final Record actualRecord = exportedRecords.get(0);
    final LoggedEvent loggedEvent = rule.events().withPosition(position);
    final RecordMetadata metadata = new RecordMetadata();

    assertThat(actualRecord)
        .hasPosition(loggedEvent.getPosition())
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

  private ExporterDescriptor createMockedExporter() {
    exporter = new ControlledTestExporter();

    final ExporterDescriptor descriptor =
        spy(new ExporterDescriptor("test-exporter", exporter.getClass(), Collections.emptyMap()));
    doAnswer(c -> exporter).when(descriptor).newInstance();

    return descriptor;
  }
}
