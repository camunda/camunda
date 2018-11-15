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

import io.zeebe.broker.exporter.ExporterObjectMapper;
import io.zeebe.broker.exporter.record.RecordImpl;
import io.zeebe.broker.exporter.record.value.IncidentRecordValueImpl;
import io.zeebe.broker.exporter.record.value.JobBatchRecordValueImpl;
import io.zeebe.broker.exporter.record.value.JobRecordValueImpl;
import io.zeebe.broker.exporter.record.value.MessageSubscriptionRecordValueImpl;
import io.zeebe.broker.exporter.record.value.RaftRecordValueImpl;
import io.zeebe.broker.exporter.record.value.TimerRecordValueImpl;
import io.zeebe.broker.exporter.record.value.WorkflowInstanceRecordValueImpl;
import io.zeebe.broker.exporter.record.value.WorkflowInstanceSubscriptionRecordValueImpl;
import io.zeebe.broker.exporter.record.value.deployment.DeployedWorkflowImpl;
import io.zeebe.broker.exporter.record.value.deployment.DeploymentResourceImpl;
import io.zeebe.broker.exporter.record.value.job.HeadersImpl;
import io.zeebe.broker.exporter.record.value.raft.RaftMemberImpl;
import io.zeebe.broker.incident.data.IncidentRecord;
import io.zeebe.broker.subscription.message.data.MessageSubscriptionRecord;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.workflow.data.TimerRecord;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordMetadata;
import io.zeebe.exporter.record.RecordValue;
import io.zeebe.exporter.record.value.DeploymentRecordValue;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.exporter.record.value.MessageRecordValue;
import io.zeebe.exporter.record.value.MessageSubscriptionRecordValue;
import io.zeebe.exporter.record.value.RaftRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.exporter.record.value.deployment.DeployedWorkflow;
import io.zeebe.exporter.record.value.deployment.DeploymentResource;
import io.zeebe.exporter.record.value.deployment.ResourceType;
import io.zeebe.exporter.record.value.raft.RaftMember;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.msgpack.value.LongValue;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.Workflow;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobHeaders;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.raft.event.RaftConfigurationEvent;
import io.zeebe.raft.event.RaftConfigurationEventMember;
import io.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;

public class ExporterRecordMapper {
  private final DirectBufferInputStream serderInputStream = new DirectBufferInputStream();
  private final ExporterObjectMapper objectMapper;

  public ExporterRecordMapper(final ExporterObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Record map(final LoggedEvent event, final RecordMetadata metadata) {
    final Function<LoggedEvent, ? extends RecordValue> valueSupplier;

    switch (metadata.getValueType()) {
      case DEPLOYMENT:
        valueSupplier = this::ofDeploymentRecord;
        break;
      case INCIDENT:
        valueSupplier = this::ofIncidentRecord;
        break;
      case JOB:
        valueSupplier = this::ofJobRecord;
        break;
      case MESSAGE:
        valueSupplier = this::ofMessageRecord;
        break;
      case MESSAGE_SUBSCRIPTION:
        valueSupplier = this::ofMessageSubscriptionRecord;
        break;
      case RAFT:
        valueSupplier = this::ofRaftRecord;
        break;
      case WORKFLOW_INSTANCE:
        valueSupplier = this::ofWorkflowInstanceRecord;
        break;
      case WORKFLOW_INSTANCE_SUBSCRIPTION:
        valueSupplier = this::ofWorkflowInstanceSubscriptionRecord;
        break;
      case JOB_BATCH:
        valueSupplier = this::ofJobBatchRecord;
        break;
      case TIMER:
        valueSupplier = this::ofTimerRecord;
        break;
      default:
        return null;
    }

    return newRecord(event, metadata, valueSupplier);
  }

  private <T extends RecordValue> RecordImpl<T> newRecord(
      final LoggedEvent event,
      final RecordMetadata metadata,
      final Function<LoggedEvent, T> valueSupplier) {
    return new RecordImpl<>(
        objectMapper,
        event.getKey(),
        event.getPosition(),
        Instant.ofEpochMilli(event.getTimestamp()),
        event.getRaftTerm(),
        event.getProducerId(),
        event.getSourceEventPosition(),
        metadata,
        valueSupplier.apply(event));
  }

  // VALUE SUPPLIERS
  private RaftRecordValue ofRaftRecord(final LoggedEvent event) {
    final RaftConfigurationEvent record = new RaftConfigurationEvent();
    event.readValue(record);

    final List<RaftMember> members = new ArrayList<>();
    for (final RaftConfigurationEventMember member : record.members()) {
      members.add(new RaftMemberImpl(member.getNodeId()));
    }

    return new RaftRecordValueImpl(objectMapper, members);
  }

  private JobRecordValue ofJobRecord(final LoggedEvent event) {
    final JobRecord record = new JobRecord();
    event.readValue(record);

    return ofJobRecord(record);
  }

  private JobRecordValue ofJobRecord(JobRecord record) {
    final JobHeaders jobHeaders = record.getHeaders();
    final HeadersImpl headers =
        new HeadersImpl(
            asString(jobHeaders.getBpmnProcessId()),
            asString(jobHeaders.getElementId()),
            jobHeaders.getElementInstanceKey(),
            jobHeaders.getWorkflowInstanceKey(),
            jobHeaders.getWorkflowKey(),
            jobHeaders.getWorkflowDefinitionVersion());

    final Instant deadline;
    if (record.getDeadline() != Protocol.INSTANT_NULL_VALUE) {
      deadline = Instant.ofEpochMilli(record.getDeadline());
    } else {
      deadline = null;
    }

    return new JobRecordValueImpl(
        objectMapper,
        asJson(record.getPayload()),
        asString(record.getType()),
        asString(record.getWorker()),
        deadline,
        headers,
        asMsgPackMap(record.getCustomHeaders()),
        record.getRetries(),
        asString(record.getErrorMessage()));
  }

  private DeploymentRecordValue ofDeploymentRecord(final LoggedEvent event) {
    final List<DeployedWorkflow> deployedWorkflows = new ArrayList<>();
    final List<DeploymentResource> resources = new ArrayList<>();
    final DeploymentRecord record = new DeploymentRecord();

    event.readValue(record);

    for (final Workflow workflow : record.workflows()) {
      deployedWorkflows.add(
          new DeployedWorkflowImpl(
              asString(workflow.getBpmnProcessId()),
              asString(workflow.getResourceName()),
              workflow.getKey(),
              workflow.getVersion()));
    }

    for (final io.zeebe.protocol.impl.record.value.deployment.DeploymentResource resource :
        record.resources()) {
      resources.add(
          new DeploymentResourceImpl(
              asByteArray(resource.getResource()),
              asResourceType(resource.getResourceType()),
              asString(resource.getResourceName())));
    }

    return new io.zeebe.broker.exporter.record.value.DeploymentRecordValueImpl(
        objectMapper, deployedWorkflows, resources);
  }

  private IncidentRecordValue ofIncidentRecord(final LoggedEvent event) {
    final IncidentRecord record = new IncidentRecord();
    event.readValue(record);

    return new IncidentRecordValueImpl(
        objectMapper,
        record.getErrorType().name(),
        asString(record.getErrorMessage()),
        asString(record.getBpmnProcessId()),
        asString(record.getElementId()),
        record.getWorkflowInstanceKey(),
        record.getElementInstanceKey(),
        record.getJobKey());
  }

  private MessageRecordValue ofMessageRecord(final LoggedEvent event) {
    final MessageRecord record = new MessageRecord();
    event.readValue(record);

    return new io.zeebe.broker.exporter.record.value.MessageRecordValueImpl(
        objectMapper,
        asJson(record.getPayload()),
        asString(record.getName()),
        asString(record.getMessageId()),
        asString(record.getCorrelationKey()),
        record.getTimeToLive());
  }

  private MessageSubscriptionRecordValue ofMessageSubscriptionRecord(final LoggedEvent event) {
    final MessageSubscriptionRecord record = new MessageSubscriptionRecord();
    event.readValue(record);

    return new MessageSubscriptionRecordValueImpl(
        objectMapper,
        asString(record.getMessageName()),
        asString(record.getCorrelationKey()),
        record.getWorkflowInstanceKey(),
        record.getElementInstanceKey());
  }

  private WorkflowInstanceRecordValue ofWorkflowInstanceRecord(final LoggedEvent event) {
    final WorkflowInstanceRecord record = new WorkflowInstanceRecord();
    event.readValue(record);

    return new WorkflowInstanceRecordValueImpl(
        objectMapper,
        asJson(record.getPayload()),
        asString(record.getBpmnProcessId()),
        asString(record.getElementId()),
        record.getVersion(),
        record.getWorkflowKey(),
        record.getWorkflowInstanceKey(),
        record.getScopeInstanceKey());
  }

  private WorkflowInstanceSubscriptionRecordValue ofWorkflowInstanceSubscriptionRecord(
      final LoggedEvent event) {
    final WorkflowInstanceSubscriptionRecord record = new WorkflowInstanceSubscriptionRecord();
    event.readValue(record);

    return new WorkflowInstanceSubscriptionRecordValueImpl(
        objectMapper,
        asJson(record.getPayload()),
        asString(record.getMessageName()),
        record.getWorkflowInstanceKey(),
        record.getElementInstanceKey());
  }

  private RecordValue ofJobBatchRecord(LoggedEvent event) {
    final JobBatchRecord record = new JobBatchRecord();
    event.readValue(record);

    final List<Long> jobKeys =
        StreamSupport.stream(record.jobKeys().spliterator(), false)
            .map(LongValue::getValue)
            .collect(Collectors.toList());

    final List<JobRecordValue> jobs =
        StreamSupport.stream(record.jobs().spliterator(), false)
            .map(this::ofJobRecord)
            .collect(Collectors.toList());

    return new JobBatchRecordValueImpl(
        objectMapper,
        asString(record.getType()),
        asString(record.getWorker()),
        Duration.ofMillis(record.getTimeout()),
        record.getAmount(),
        jobKeys,
        jobs);
  }

  private RecordValue ofTimerRecord(LoggedEvent event) {
    final TimerRecord record = new TimerRecord();
    event.readValue(record);

    return new TimerRecordValueImpl(
        objectMapper,
        record.getElementInstanceKey(),
        record.getDueDate(),
        asString(record.getHandlerNodeId()));
  }

  // UTILS

  private byte[] asByteArray(final DirectBuffer buffer) {
    return BufferUtil.bufferAsArray(buffer);
  }

  private String asString(final DirectBuffer buffer) {
    return BufferUtil.bufferAsString(buffer);
  }

  private Map<String, Object> asMsgPackMap(final DirectBuffer msgPackEncoded) {
    serderInputStream.wrap(msgPackEncoded);
    return objectMapper.fromMsgpackAsMap(serderInputStream);
  }

  private String asJson(final DirectBuffer msgPackEncoded) {
    serderInputStream.wrap(msgPackEncoded);
    return objectMapper.getMsgPackConverter().convertToJson(serderInputStream);
  }

  private ResourceType asResourceType(
      final io.zeebe.protocol.impl.record.value.deployment.ResourceType resourceType) {
    switch (resourceType) {
      case BPMN_XML:
        return ResourceType.BPMN_XML;
      case YAML_WORKFLOW:
        return ResourceType.YAML_WORKFLOW;
    }
    throw new IllegalArgumentException("Provided resource type does not exist " + resourceType);
  }
}
