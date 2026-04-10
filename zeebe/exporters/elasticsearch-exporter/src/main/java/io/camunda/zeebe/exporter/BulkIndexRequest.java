/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import io.camunda.zeebe.exporter.dto.BulkIndexAction;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedDecisionValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationTerminateInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ResourceDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.management.CheckpointRecordValue;
import io.camunda.zeebe.util.SemanticVersion;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Buffers indexing requests of records. Each bulk operation is serialized before being buffered to
 * avoid having to serialize it again on retry.
 */
final class BulkIndexRequest {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .addMixIn(Record.class, RecordSequenceMixin.class)
          .addMixIn(EvaluatedDecisionValue.class, EvaluatedDecisionMixin.class)
          .addMixIn(CommandDistributionRecordValue.class, CommandDistributionMixin.class)
          .enable(Feature.ALLOW_SINGLE_QUOTES);

  private static final ObjectMapper PREVIOUS_VERSION_MAPPER =
      new ObjectMapper()
          .addMixIn(Record.class, RecordSequenceMixin.class)
          .addMixIn(CommandDistributionRecordValue.class, CommandDistributionMixin.class)
          .addMixIn(CheckpointRecordValue.class, CheckpointRecordMixin.class)
          .addMixIn(DecisionEvaluationRecordValue.class, IgnoreRootProcessInstanceKeyMixin.class)
          .addMixIn(DecisionRequirementsRecordValue.class, DeploymentKeyMixin.class)
          .addMixIn(DecisionRequirementsMetadataValue.class, DeploymentKeyMixin.class)
          .addMixIn(DeploymentRecordValue.class, DeploymentMixin.class)
          .addMixIn(EvaluatedDecisionValue.class, EvaluatedDecisionMixin.class)
          .addMixIn(IncidentRecordValue.class, IgnoreRootProcessInstanceKeyMixin.class)
          .addMixIn(JobBatchRecordValue.class, JobBatchMixin.class)
          .addMixIn(
              JobRecordValue.class,
              IgnoreRootProcessInstanceKeyAndJobToUserTaskMigrationMixin.class)
          .addMixIn(MessageCorrelationRecordValue.class, ProcessDefinitionKeyMixin.class)
          .addMixIn(MessageSubscriptionRecordValue.class, MessageSubscriptionMixin.class)
          .addMixIn(
              ProcessInstanceRecordValue.class,
              IgnoreRootProcessInstanceKeyAndBusinessIdAndElementInstanceKeyMixin.class)
          .addMixIn(ProcessInstanceBatchRecordValue.class, ProcessDefinitionKeyMixin.class)
          .addMixIn(
              ProcessMessageSubscriptionRecordValue.class, ProcessMessageSubscriptionMixin.class)
          .addMixIn(
              ProcessInstanceModificationRecordValue.class, ProcessInstanceModificationMixin.class)
          .addMixIn(
              ProcessInstanceCreationRecordValue.class,
              IgnoreRootProcessInstanceKeyAndBusinessIdAndElementInstanceKeyMixin.class)
          .addMixIn(ProcessInstanceMigrationRecordValue.class, ProcessInstanceMigrationMixin.class)
          .addMixIn(
              ProcessInstanceModificationTerminateInstructionValue.class,
              TerminateInstructionsMixin.class)
          .addMixIn(ResourceDeletionRecordValue.class, ResourceDeletionMixin.class)
          .addMixIn(RuntimeInstructionRecordValue.class, ProcessDefinitionKeyMixin.class)
          .addMixIn(UserTaskRecordValue.class, UserTaskMixin.class)
          .addMixIn(
              VariableRecordValue.class, IgnoreRootProcessInstanceKeyAndAuditFieldsMixin.class)
          .enable(Feature.ALLOW_SINGLE_QUOTES);

  // The property of the ES record template to store the sequence of the record.
  private static final String RECORD_SEQUENCE_PROPERTY = "sequence";
  private static final String RECORD_AUTHORIZATIONS_PROPERTY = "authorizations";
  private static final String RECORD_AGENT_PROPERTY = "agent";
  private static final String RECORD_DECISION_EVALUATION_INSTANCE_KEY_PROPERTY =
      "decisionEvaluationInstanceKey";
  private static final String AUTH_INFO_PROPERTY = "authInfo";
  private static final String CHECKPOINT_TIMESTAMP_PROPERTY = "checkpointTimestamp";
  private static final String CHECKPOINT_TYPE_PROPERTY = "checkpointType";
  private static final String CHECKPOINT_FIRST_LOG_POSITION_PROPERTY = "firstLogPosition";
  private static final String DEFINITION_KEY_PROPERTY = "processDefinitionKey";
  private static final String PROCESS_INSTANCE_MODIFICATION_MOVE_INSTRUCTIONS_PROPERTY =
      "moveInstructions";
  private static final String ROOT_PROCESS_INSTANCE_KEY_PROPERTY = "rootProcessInstanceKey";
  private static final String BUSINESS_ID_PROPERTY = "businessId";
  private static final String JOB_TO_USER_TASK_MIGRATION_PROPERTY = "jobToUserTaskMigration";
  private static final String BPMN_PROCESS_ID_PROPERTY = "bpmnProcessId";
  private static final String ELEMENT_INSTANCE_KEY_PROPERTY = "elementInstanceKey";
  private static final String VARIABLE_SOURCE_PROPERTY = "source";
  private static final String TENANT_FILTER_PROPERTY = "tenantFilter";
  private static final String TAGS_PROPERTY = "tags";
  private static final String LISTENERS_CONFIG_KEY_PROPERTY = "listenersConfigKey";
  private static final String DEPLOYMENT_KEY_PROPERTY = "deploymentKey";
  private static final String DELETE_HISTORY_PROPERTY = "deleteHistory";
  private static final String BATCH_OPERATION_KEY_PROPERTY = "batchOperationKey";
  private static final String BATCH_OPERATION_TYPE_PROPERTY = "batchOperationType";
  private static final String RESOURCE_TYPE_PROPERTY = "resourceType";
  private static final String RESOURCE_ID_PROPERTY = "resourceId";
  private static final String TENANT_ID_PROPERTY = "tenantId";
  private final List<IndexOperation> operations = new ArrayList<>();
  private BulkIndexAction lastIndexedMetadata;
  private int memoryUsageBytes = 0;

  /**
   * Indexes the given record for the given bulk action. See
   * https://www.elastic.co/guide/en/elasticsearch/reference/7.17/docs-bulk.html for the types of
   * actions.
   *
   * <p>The call is a no-op if the last indexed action is the same as the given one.
   *
   * @param action the bulk action to take
   * @param record the record that will be the source of the document
   * @param recordSequence the sequence number of the record
   * @return true if the record was appended to the batch, false if the record is already indexed in
   *     the batch because only one copy of the record is allowed in the batch
   */
  boolean index(
      final BulkIndexAction action, final Record<?> record, final RecordSequence recordSequence) {
    // exit early in case we're retrying the last indexed record again
    if (lastIndexedMetadata != null && lastIndexedMetadata.equals(action)) {
      return false;
    }

    final byte[] source;
    try {
      source = serializeRecord(record, recordSequence);

    } catch (final IOException e) {
      throw new ElasticsearchExporterException(
          String.format("Failed to serialize record to JSON for indexing action %s", action), e);
    }

    final IndexOperation command = new IndexOperation(action, source);
    memoryUsageBytes += command.source().length;
    lastIndexedMetadata = action;
    operations.add(command);
    return true;
  }

  private static byte[] serializeRecord(final Record<?> record, final RecordSequence recordSequence)
      throws IOException {
    final var mapper =
        isPreviousVersionRecord(record.getBrokerVersion()) ? PREVIOUS_VERSION_MAPPER : MAPPER;
    return mapper
        .writer()
        // Enhance the serialized record by its sequence number. The sequence number is not a part
        // of the record itself but a special property for Elasticsearch. It can be used to limit
        // the number of records when reading from the index, for example, by using a range query.
        // Read https://github.com/camunda/camunda/issues/10568 for details.
        .withAttribute(RECORD_SEQUENCE_PROPERTY, recordSequence.sequence())
        .writeValueAsBytes(record);
  }

  /** Returns the number of operations indexed so far. */
  int size() {
    return operations.size();
  }

  /** Returns an approximate amount of memory used by this buffer. */
  int memoryUsageBytes() {
    return memoryUsageBytes;
  }

  /** Returns true if no operations were indexed, i.e. {@link #size()} is 0, false otherwise. */
  boolean isEmpty() {
    return operations.isEmpty();
  }

  /** Clears the buffer entirely. */
  void clear() {
    operations.clear();
    memoryUsageBytes = 0;
    lastIndexedMetadata = null;
  }

  /** Returns the last action metadata indexed. May be null. */
  BulkIndexAction lastIndexedMetadata() {
    return lastIndexedMetadata;
  }

  /** Returns the currently indexed operations as an unmodifiable shallow copy. */
  List<IndexOperation> bulkOperations() {
    return Collections.unmodifiableList(operations);
  }

  private static boolean isPreviousVersionRecord(final String brokerVersion) {
    final SemanticVersion semanticVersion =
        SemanticVersion.parse(brokerVersion)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Expected to parse valid semantic version, but got [%s]"
                            .formatted(brokerVersion)));
    final int currentMinorVersion =
        VersionUtil.getSemanticVersion()
            .map(SemanticVersion::minor)
            .orElseThrow(
                () -> new IllegalStateException("Expected to have a valid semantic version"));
    return semanticVersion.minor() < currentMinorVersion;
  }

  record IndexOperation(BulkIndexAction metadata, byte[] source) {}

  @JsonAppend(attrs = {@JsonAppend.Attr(value = RECORD_SEQUENCE_PROPERTY)})
  @JsonIgnoreProperties({RECORD_AUTHORIZATIONS_PROPERTY, RECORD_AGENT_PROPERTY})
  private static final class RecordSequenceMixin {}

  @JsonIgnoreProperties({RECORD_DECISION_EVALUATION_INSTANCE_KEY_PROPERTY})
  private static final class EvaluatedDecisionMixin {}

  @JsonIgnoreProperties({AUTH_INFO_PROPERTY})
  private static final class CommandDistributionMixin {}

  @JsonIgnoreProperties({
    CHECKPOINT_TYPE_PROPERTY,
    CHECKPOINT_TIMESTAMP_PROPERTY,
    CHECKPOINT_FIRST_LOG_POSITION_PROPERTY
  })
  private static final class CheckpointRecordMixin {}

  @JsonIgnoreProperties({DEFINITION_KEY_PROPERTY})
  private static final class MessageSubscriptionMixin {}

  @JsonIgnoreProperties({DEFINITION_KEY_PROPERTY, ROOT_PROCESS_INSTANCE_KEY_PROPERTY})
  private static final class ProcessMessageSubscriptionMixin {}

  @JsonIgnoreProperties({
    PROCESS_INSTANCE_MODIFICATION_MOVE_INSTRUCTIONS_PROPERTY,
    ROOT_PROCESS_INSTANCE_KEY_PROPERTY,
    BPMN_PROCESS_ID_PROPERTY,
    ELEMENT_INSTANCE_KEY_PROPERTY,
    DEFINITION_KEY_PROPERTY
  })
  private static final class ProcessInstanceModificationMixin {}

  @JsonIgnoreProperties({ROOT_PROCESS_INSTANCE_KEY_PROPERTY})
  private static final class IgnoreRootProcessInstanceKeyMixin {}

  @JsonIgnoreProperties({
    ROOT_PROCESS_INSTANCE_KEY_PROPERTY,
    BUSINESS_ID_PROPERTY,
    ELEMENT_INSTANCE_KEY_PROPERTY
  })
  private static final class IgnoreRootProcessInstanceKeyAndBusinessIdAndElementInstanceKeyMixin {}

  @JsonIgnoreProperties({
    ROOT_PROCESS_INSTANCE_KEY_PROPERTY,
    BPMN_PROCESS_ID_PROPERTY,
    ELEMENT_INSTANCE_KEY_PROPERTY,
    DEFINITION_KEY_PROPERTY,
    TENANT_ID_PROPERTY
  })
  private static final class ProcessInstanceMigrationMixin {}

  @JsonIgnoreProperties({
    ROOT_PROCESS_INSTANCE_KEY_PROPERTY,
    ELEMENT_INSTANCE_KEY_PROPERTY,
    VARIABLE_SOURCE_PROPERTY
  })
  private static final class IgnoreRootProcessInstanceKeyAndAuditFieldsMixin {}

  @JsonIgnoreProperties({ROOT_PROCESS_INSTANCE_KEY_PROPERTY, JOB_TO_USER_TASK_MIGRATION_PROPERTY})
  private static final class IgnoreRootProcessInstanceKeyAndJobToUserTaskMigrationMixin {}

  @JsonIgnoreProperties({TENANT_FILTER_PROPERTY})
  private static final class JobBatchMixin {}

  @JsonIgnoreProperties({
    ROOT_PROCESS_INSTANCE_KEY_PROPERTY,
    TAGS_PROPERTY,
    LISTENERS_CONFIG_KEY_PROPERTY
  })
  private static final class UserTaskMixin {}

  @JsonIgnoreProperties({DEPLOYMENT_KEY_PROPERTY})
  private static final class DeploymentKeyMixin {}

  @JsonIgnoreProperties({DEPLOYMENT_KEY_PROPERTY})
  private static final class DeploymentMixin {}

  @JsonIgnoreProperties({
    DELETE_HISTORY_PROPERTY,
    BATCH_OPERATION_KEY_PROPERTY,
    BATCH_OPERATION_TYPE_PROPERTY,
    RESOURCE_TYPE_PROPERTY,
    RESOURCE_ID_PROPERTY
  })
  private static final class ResourceDeletionMixin {}

  @JsonIgnoreProperties({DEFINITION_KEY_PROPERTY})
  private static final class ProcessDefinitionKeyMixin {}

  public interface TerminateInstructionsMixin {
    @JsonIgnore
    String getElementId();
  }
}
