/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import static io.camunda.zeebe.protocol.record.ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION;
import static io.camunda.zeebe.protocol.record.ValueType.ASYNC_REQUEST;
import static io.camunda.zeebe.protocol.record.ValueType.AUTHORIZATION;
import static io.camunda.zeebe.protocol.record.ValueType.CHECKPOINT;
import static io.camunda.zeebe.protocol.record.ValueType.CLUSTER_VARIABLE;
import static io.camunda.zeebe.protocol.record.ValueType.COMMAND_DISTRIBUTION;
import static io.camunda.zeebe.protocol.record.ValueType.COMPENSATION_SUBSCRIPTION;
import static io.camunda.zeebe.protocol.record.ValueType.CONDITIONAL_EVALUATION;
import static io.camunda.zeebe.protocol.record.ValueType.CONDITIONAL_SUBSCRIPTION;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION_EVALUATION;
import static io.camunda.zeebe.protocol.record.ValueType.DECISION_REQUIREMENTS;
import static io.camunda.zeebe.protocol.record.ValueType.DEPLOYMENT;
import static io.camunda.zeebe.protocol.record.ValueType.DEPLOYMENT_DISTRIBUTION;
import static io.camunda.zeebe.protocol.record.ValueType.ESCALATION;
import static io.camunda.zeebe.protocol.record.ValueType.EXPRESSION;
import static io.camunda.zeebe.protocol.record.ValueType.GLOBAL_LISTENER;
import static io.camunda.zeebe.protocol.record.ValueType.GROUP;
import static io.camunda.zeebe.protocol.record.ValueType.IDENTITY_SETUP;
import static io.camunda.zeebe.protocol.record.ValueType.MAPPING_RULE;
import static io.camunda.zeebe.protocol.record.ValueType.MULTI_INSTANCE;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_CREATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MIGRATION;
import static io.camunda.zeebe.protocol.record.ValueType.PROCESS_INSTANCE_MODIFICATION;
import static io.camunda.zeebe.protocol.record.ValueType.ROLE;
import static io.camunda.zeebe.protocol.record.ValueType.RUNTIME_INSTRUCTION;
import static io.camunda.zeebe.protocol.record.ValueType.SIGNAL;
import static io.camunda.zeebe.protocol.record.ValueType.SIGNAL_SUBSCRIPTION;
import static io.camunda.zeebe.protocol.record.ValueType.TIMER;
import static io.camunda.zeebe.protocol.record.ValueType.USAGE_METRIC;
import static io.camunda.zeebe.protocol.record.ValueType.USER_TASK;
import static io.camunda.zeebe.protocol.record.ValueType.VARIABLE;
import static io.camunda.zeebe.protocol.record.ValueType.VARIABLE_DOCUMENT;
import static io.camunda.zeebe.protocol.record.intent.MultiInstanceIntent.INPUT_COLLECTION_EVALUATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.ASSIGNMENT_DENIED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.COMPLETE_TASK_LISTENER;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.COMPLETION_DENIED;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.DENY_TASK_LISTENER;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.UPDATE_DENIED;
import static io.camunda.zeebe.protocol.record.value.JobKind.EXECUTION_LISTENER;
import static io.camunda.zeebe.protocol.record.value.JobKind.TASK_LISTENER;
import static java.util.Comparator.comparing;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.apache.commons.lang3.StringUtils.leftPad;
import static org.apache.commons.lang3.StringUtils.rightPad;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValueWithVariables;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.AsyncRequestRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.BatchOperationChunkRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationInitializationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationPartitionLifecycleRecordValue;
import io.camunda.zeebe.protocol.record.value.ClockRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.CompensationSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ConditionalEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorRecordValue;
import io.camunda.zeebe.protocol.record.value.EscalationRecordValue;
import io.camunda.zeebe.protocol.record.value.ExpressionRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.IdentitySetupRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobMetricsBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MultiInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationStartInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationActivateInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationMoveInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationTerminateInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationVariableInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ResourceDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.protocol.record.value.management.CheckpointRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.ScaleRecordValue;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompactRecordLogger {

  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.test");
  private static final String BLOCK_SEPARATOR = " ";

  // List rather than Map to preserve order
  private static final List<Entry<String, String>> ABBREVIATIONS =
      List.of(
          entry(COMPLETE_TASK_LISTENER.name(), "COMP_TL"),
          entry(DENY_TASK_LISTENER.name(), "DENY_TL"),
          entry(TASK_LISTENER.name(), "TL"),
          entry(COMPLETE_EXECUTION_LISTENER.name(), "COMP_EL"),
          entry(EXECUTION_LISTENER.name(), "EL"),
          entry(ASSIGNMENT_DENIED.name(), "ASGN_DENIED"),
          entry(COMPLETION_DENIED.name(), "COMP_DENIED"),
          entry(UPDATE_DENIED.name(), "UPDT_DENIED"),
          entry(SEQUENCE_FLOW_TAKEN.name(), "SQ_FLW_TKN"),
          entry(AD_HOC_SUB_PROCESS_INSTRUCTION.name(), "AHSP_INST"),
          entry(PROCESS_INSTANCE_CREATION.name(), "CREA"),
          entry(PROCESS_INSTANCE_MODIFICATION.name(), "MOD"),
          entry(PROCESS_INSTANCE_MIGRATION.name(), "PI_MIGR"),
          entry(PROCESS_INSTANCE.name(), "PI"),
          entry("PROCESS_INSTANCE", "PI"), // partial matches in ValueType and ProcessInstanceIntent
          entry(RUNTIME_INSTRUCTION.name(), "RI"),
          entry("PROCESS", "PROC"), // partial matches in ValueType
          entry(TIMER.name(), "TIME"),
          entry("MESSAGE", "MSG"), // partial matches in ValueType
          entry("SUBSCRIPTION", "SUB"), // partial matches in ValueType
          entry("SEQUENCE", "SEQ"), // partial matches in ProcessInstanceIntent
          entry(DEPLOYMENT_DISTRIBUTION.name(), "DPLY_DSTR"),
          entry(DEPLOYMENT.name(), "DPLY"),
          entry(VARIABLE_DOCUMENT.name(), "VAR_DOC"),
          entry(VARIABLE.name(), "VAR"),
          entry("ELEMENT_", ""), // partial matches in ProcessInstanceIntent
          entry("_ELEMENT", ""), // partial matches in ProcessInstanceIntent
          entry("EVENT", "EVNT"), // partial matches in ValueType
          entry(DECISION_REQUIREMENTS.name(), "DRG"),
          entry(DECISION_EVALUATION.name(), "DECISION_EVAL"),
          entry(SIGNAL_SUBSCRIPTION.name(), "SIG_SUB"),
          entry(SIGNAL.name(), "SIG"),
          entry(COMMAND_DISTRIBUTION.name(), "DSTR"),
          entry(USER_TASK.name(), "UT"),
          entry(ROLE.name(), "RL"),
          entry(GROUP.name(), "GR"),
          entry(MAPPING_RULE.name(), "MAP_RULE"),
          entry(ASYNC_REQUEST.name(), "ASYNC"),
          entry(MULTI_INSTANCE.name(), "MI"),
          entry(INPUT_COLLECTION_EVALUATED.name(), "IN_COL_EVAL"),
          entry("BATCH_OPERATION", "BO"), // partial matches in ValueType
          entry(AUTHORIZATION.name(), "AUTH"),
          entry(COMPENSATION_SUBSCRIPTION.name(), "COMP_SUB"),
          entry(USAGE_METRIC.name(), "USG_MTRC"),
          entry(CREATE_WITH_AWAITING_RESULT.name(), "WITH_RESULT"),
          entry(ESCALATION.name(), "ESC"),
          entry(CLUSTER_VARIABLE.name(), "CLSTR_VAR"),
          entry(CONDITIONAL_SUBSCRIPTION.name(), "COND_SUB"),
          entry(CONDITIONAL_EVALUATION.name(), "COND_EVAL"),
          entry(EXPRESSION.name(), "EXPR"),
          entry(IDENTITY_SETUP.name(), "ID"),
          entry(CHECKPOINT.name(), "CHK"),
          entry(GLOBAL_LISTENER.name(), "GL"));

  private static final Map<RecordType, Character> RECORD_TYPE_ABBREVIATIONS =
      ofEntries(
          entry(RecordType.COMMAND, 'C'),
          entry(RecordType.EVENT, 'E'),
          entry(RecordType.COMMAND_REJECTION, 'R'));

  private final Map<ValueType, Function<Record<?>, String>> valueLoggers = new HashMap<>();

  private final int keyDigits;
  private final int valueTypeChars;
  private final int intentChars;
  private final boolean multiPartition;
  private final boolean hasTimerEvents;
  private final Map<Long, String> substitutions = new HashMap<>();
  private final ArrayList<Record<?>> records;

  private final List<Process> processes = new ArrayList<>();
  private ObjectMapper objectMapper;

  {
    valueLoggers.put(ValueType.NULL_VAL, this::summarizeMiscValue);
    valueLoggers.put(ValueType.SBE_UNKNOWN, this::summarizeMiscValue);
    valueLoggers.put(ValueType.DEPLOYMENT, this::summarizeDeployment);
    valueLoggers.put(ValueType.DEPLOYMENT_DISTRIBUTION, this::summarizeDeploymentDistribution);
    valueLoggers.put(ValueType.PROCESS, this::summarizeProcess);
    valueLoggers.put(ValueType.INCIDENT, this::summarizeIncident);
    valueLoggers.put(ValueType.JOB, this::summarizeJob);
    valueLoggers.put(ValueType.JOB_BATCH, this::summarizeJobBatch);
    valueLoggers.put(ValueType.MESSAGE, this::summarizeMessage);
    valueLoggers.put(ValueType.MESSAGE_BATCH, this::summarizeMessageBatch);
    valueLoggers.put(
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, this::summarizeMessageStartEventSubscription);
    valueLoggers.put(ValueType.MESSAGE_SUBSCRIPTION, this::summarizeMessageSubscription);
    valueLoggers.put(ValueType.PROCESS_INSTANCE, this::summarizeProcessInstance);
    valueLoggers.put(ValueType.PROCESS_INSTANCE_BATCH, this::summarizeProcessInstanceBatch);
    valueLoggers.put(ValueType.PROCESS_INSTANCE_CREATION, this::summarizeProcessInstanceCreation);
    valueLoggers.put(
        ValueType.PROCESS_INSTANCE_MODIFICATION, this::summarizeProcessInstanceModification);
    valueLoggers.put(
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION, this::summarizeProcessInstanceSubscription);
    valueLoggers.put(
        ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION, this::summarizeAdHocSubProcessInstruction);
    valueLoggers.put(ValueType.VARIABLE, this::summarizeVariable);
    valueLoggers.put(ValueType.VARIABLE_DOCUMENT, this::summarizeVariableDocument);
    valueLoggers.put(ValueType.TIMER, this::summarizeTimer);
    valueLoggers.put(ValueType.ERROR, this::summarizeError);
    valueLoggers.put(ValueType.PROCESS_EVENT, this::summarizeProcessEvent);
    valueLoggers.put(ValueType.DECISION_REQUIREMENTS, this::summarizeDecisionRequirements);
    valueLoggers.put(ValueType.DECISION, this::summarizeDecision);
    valueLoggers.put(ValueType.DECISION_EVALUATION, this::summarizeDecisionEvaluation);
    valueLoggers.put(ValueType.SIGNAL, this::summarizeSignal);
    valueLoggers.put(ValueType.SIGNAL_SUBSCRIPTION, this::summarizeSignalSubscription);
    valueLoggers.put(ValueType.USER_TASK, this::summarizeUserTask);
    valueLoggers.put(ValueType.COMMAND_DISTRIBUTION, this::summarizeCommandDistribution);
    valueLoggers.put(ValueType.MESSAGE_CORRELATION, this::summarizeMessageCorrelation);
    valueLoggers.put(ValueType.CLOCK, this::summarizeClock);
    valueLoggers.put(ValueType.ROLE, this::summarizeRole);
    valueLoggers.put(ValueType.TENANT, this::summarizeTenant);
    valueLoggers.put(ValueType.GROUP, this::summarizeGroup);
    valueLoggers.put(ValueType.MAPPING_RULE, this::summarizeMappingRule);
    valueLoggers.put(ValueType.ASYNC_REQUEST, this::summarizeAsyncRequest);
    valueLoggers.put(ValueType.USER, this::summarizeUser);
    valueLoggers.put(ValueType.AUTHORIZATION, this::summarizeAuthorization);
    valueLoggers.put(ValueType.RESOURCE, this::summarizeResource);
    valueLoggers.put(ValueType.RESOURCE_DELETION, this::summarizeResourceDeletion);
    valueLoggers.put(ValueType.COMPENSATION_SUBSCRIPTION, this::summarizeCompensationSubscription);
    valueLoggers.put(ValueType.MULTI_INSTANCE, this::summarizeMultiInstance);
    valueLoggers.put(ValueType.RUNTIME_INSTRUCTION, this::summarizeRuntimeInstruction);
    valueLoggers.put(ValueType.BATCH_OPERATION_CREATION, this::summarizeBatchOperationCreation);
    valueLoggers.put(
        ValueType.BATCH_OPERATION_INITIALIZATION, this::summarizeBatchOperationInitialization);
    valueLoggers.put(ValueType.BATCH_OPERATION_CHUNK, this::summarizeBatchOperationChunk);
    valueLoggers.put(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, this::summarizeBatchOperationLifecycle);
    valueLoggers.put(
        ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE,
        this::summarizeBatchOperationPartitionLifecycle);
    valueLoggers.put(ValueType.BATCH_OPERATION_EXECUTION, this::summarizeBatchOperationExecution);
    valueLoggers.put(ValueType.USAGE_METRIC, this::summarizeUsageMetrics);
    valueLoggers.put(ValueType.PROCESS_INSTANCE_RESULT, this::summarizeProcessInstanceResult);
    valueLoggers.put(ValueType.ESCALATION, this::summarizeEscalation);
    valueLoggers.put(ValueType.PROCESS_INSTANCE_MIGRATION, this::summarizeProcessInstanceMigration);
    valueLoggers.put(CLUSTER_VARIABLE, this::summarizeClusterVariable);
    valueLoggers.put(ValueType.CONDITIONAL_SUBSCRIPTION, this::summarizeConditionalSubscription);
    valueLoggers.put(CONDITIONAL_EVALUATION, this::summarizeConditionalEvaluation);
    valueLoggers.put(EXPRESSION, this::summarizeExpression);
    valueLoggers.put(ValueType.IDENTITY_SETUP, this::summarizeIdentitySetup);
    valueLoggers.put(ValueType.SCALE, this::summarizeScale);
    valueLoggers.put(ValueType.CHECKPOINT, this::summarizeCheckpoint);
    valueLoggers.put(ValueType.FORM, this::summarizeForm);
    valueLoggers.put(ValueType.HISTORY_DELETION, this::summarizeHistoryDeletion);
    valueLoggers.put(ValueType.GLOBAL_LISTENER_BATCH, this::summarizeGlobalListenerBatch);
    valueLoggers.put(ValueType.JOB_METRICS_BATCH, this::summarizeJobMetricsBatch);
    valueLoggers.put(ValueType.GLOBAL_LISTENER, this::summarizeGlobalListener);
  }

  public CompactRecordLogger(final Collection<Record<?>> records) {
    this.records = new ArrayList<>(records);
    multiPartition = isMultiPartition();
    hasTimerEvents = records.stream().anyMatch(r -> r.getValueType() == ValueType.TIMER);

    final var highestPosition = this.records.isEmpty() ? 0 : this.records.getLast().getPosition();

    int digits = 0;
    long num = highestPosition;
    while (num != 0) {
      // num = num/10
      num /= 10;
      ++digits;
    }

    keyDigits = digits;

    valueTypeChars =
        this.records.stream()
            .map(Record::getValueType)
            .map(ValueType::name)
            .map(this::abbreviate)
            .mapToInt(String::length)
            .max()
            .orElse(0);

    intentChars =
        this.records.stream()
            .map(Record::getIntent)
            .map(Intent::name)
            .map(this::abbreviate)
            .mapToInt(String::length)
            .max()
            .orElse(0);
  }

  public Set<ValueType> getSupportedValueTypes() {
    return valueLoggers.keySet();
  }

  public void log() {
    final var bulkMessage = new StringBuilder().append("Compact log representation:\n");
    addSummarizedRecords(bulkMessage);

    addDeployedProcesses(bulkMessage);

    addDecomposedKeys(bulkMessage);

    LOG.info(bulkMessage.toString());
  }

  private boolean isMultiPartition() {
    final long numberOfPartitions =
        records.stream()
            .map(r -> Math.max(Protocol.decodePartitionId(r.getKey()), r.getPartitionId()))
            .filter(x -> x != -1)
            .distinct()
            .count();
    return numberOfPartitions > 1;
  }

  private void addSummarizedRecords(final StringBuilder bulkMessage) {
    bulkMessage.append("--------\n");

    bulkMessage.append("\t");
    if (hasTimerEvents) {
      bulkMessage.append("[Timestamp] ");
    }
    if (multiPartition) {
      bulkMessage.append("[Partition] ");
    }
    bulkMessage.append(
        "['C'ommand/'E'event/'R'ejection] [valueType] [intent] - #[position]->#[source record position] ");
    if (multiPartition) {
      bulkMessage.append("P[partitionId]");
    }
    bulkMessage.append(
        """
        K[key] - [summary of value]
        \tP9K999 - key; #999 - record position; "ID" element/process id; @"elementid"/[P9K999] - element with ID and key
        \tKeys are decomposed into partition id and per partition key (e.g. 2251799813685253 -> P1K005). If single partition, the partition is omitted.
        \tLong IDs are shortened (e.g. 'startEvent_5d56488e-0570-416c-ba2d-36d2a3acea78' -> 'star..acea78'
        \tHeaders defined in 'Protocol' are abbreviated (e.g. 'io.camunda.zeebe:userTaskKey:2251799813685253' -> 'uTK:K005').
        """);
    bulkMessage.append("--------\n");

    records.forEach(record -> bulkMessage.append(summarizeRecord(record)).append("\n"));
  }

  private void addDeployedProcesses(final StringBuilder bulkMessage) {
    bulkMessage.append("\n-------------- Deployed Processes ----------------------\n");
    processes.forEach(
        process ->
            bulkMessage
                .append(summarizeProcessInformation(process))
                .append(
                    String.format(
                        "[%s] ------%n%s%n",
                        formatKey(process.getProcessDefinitionKey()),
                        new String(process.getResource()))));
  }

  private void addDecomposedKeys(final StringBuilder bulkMessage) {
    bulkMessage.append("\n--------------- Decomposed keys (for debugging) -----------------\n");

    substitutions.entrySet().stream()
        .sorted(comparing(Entry::getValue))
        .forEach(
            entry ->
                bulkMessage
                    .append(entry.getValue())
                    .append(" <-> ")
                    .append(entry.getKey())
                    .append("\n"));
  }

  private StringBuilder summarizeRecord(final Record<?> record) {
    final StringBuilder message = new StringBuilder();

    message.append(summarizeTimestamp(record));
    message.append(summarizePartition(record));
    message.append(summarizeIntent(record));
    message.append(summarizePositionFields(record));
    message.append(summarizeValue(record));

    if (record.getRecordType() == RecordType.COMMAND_REJECTION) {
      message.append(" ");
      message.append(summarizeRejection(record));
    }

    return message;
  }

  private String summarizeTimestamp(final Record<?> record) {
    if (!hasTimerEvents) {
      return "";
    }
    final var timestampWithoutMillis =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(record.getTimestamp()), ZoneId.systemDefault())
            .withNano(0);
    return DateTimeFormatter.ISO_LOCAL_TIME.format(timestampWithoutMillis) + " ";
  }

  private String summarizePartition(final Record<?> record) {
    if (!multiPartition) {
      return "";
    }
    return record.getPartitionId() + " ";
  }

  private StringBuilder summarizePositionFields(final Record<?> record) {
    return new StringBuilder()
        .append(formatPosition(record.getPosition()))
        .append("->")
        .append(formatPosition(record.getSourceRecordPosition()))
        .append(" ")
        .append(shortenKey(record.getKey()))
        .append(BLOCK_SEPARATOR);
  }

  private StringBuilder summarizeIntent(final Record<?> record) {
    final var valueType = record.getValueType();

    return new StringBuilder()
        .append(RECORD_TYPE_ABBREVIATIONS.get(record.getRecordType()))
        .append(" ")
        .append(rightPad(abbreviate(valueType.name()), valueTypeChars))
        .append(" ")
        .append(rightPad(abbreviate(record.getIntent().name()), intentChars))
        .append(BLOCK_SEPARATOR);
  }

  private String summarizeValue(final Record<?> record) {
    return valueLoggers.getOrDefault(record.getValueType(), this::summarizeMiscValue).apply(record);
  }

  private String summarizeMiscValue(final Record<?> record) {
    return record.getValue().getClass().getSimpleName() + " " + record.getValue().toJson();
  }

  private String summarizeDeployment(final Record<?> record) {
    final var value = (DeploymentRecordValue) record.getValue();

    final var bpmnResources =
        value.getProcessesMetadata().stream().map(ProcessMetadataValue::getResourceName);

    final var dmnResources =
        value.getDecisionRequirementsMetadata().stream()
            .map(DecisionRequirementsMetadataValue::getResourceName);

    return Stream.concat(bpmnResources, dmnResources).collect(Collectors.joining(", "));
  }

  private String summarizeDeploymentDistribution(final Record<?> record) {
    final var value = (DeploymentDistributionRecordValue) record.getValue();
    return "on partition %d".formatted(value.getPartitionId());
  }

  private String summarizeProcess(final Record<?> record) {
    final var value = (Process) record.getValue();
    processes.add(value);
    return summarizeProcessInformation(value);
  }

  private String summarizeProcessInformation(final Process value) {
    return summarizeDeploymentMetadata(
            value,
            value.getResourceName(),
            value.getBpmnProcessId(),
            value.getProcessDefinitionKey(),
            value.getVersion(),
            value.getVersionTag(),
            value.isDuplicate())
        .toString();
  }

  private String summarizeElementInformation(
      final String elementId, final long elementInstanceKey) {
    return String.format(" @%s[%s]", formatId(elementId), shortenKey(elementInstanceKey));
  }

  private String summarizeProcessInformation(
      final String bpmnProcessId, final long processInstanceKey) {

    final var formattedProcessId =
        StringUtils.isEmpty(bpmnProcessId) ? "?" : formatId(bpmnProcessId);
    final var formattedInstanceKey = processInstanceKey < 0 ? "?" : shortenKey(processInstanceKey);

    return String.format(" in <process %s[%s]>", formattedProcessId, formattedInstanceKey);
  }

  private String summarizeProcessInformation(
      final long processDefinitionKey, final long processInstanceKey) {
    final var formattedDefinitionKey =
        processDefinitionKey < 0 ? "?" : shortenKey(processDefinitionKey);
    final var formattedInstanceKey = processInstanceKey < 0 ? "?" : shortenKey(processInstanceKey);

    return String.format(" in <process %s[%s]>", formattedDefinitionKey, formattedInstanceKey);
  }

  private String summarizeIncident(final Record<?> record) {
    final var value = (IncidentRecordValue) record.getValue();

    final var result = new StringBuilder();

    if (record.getIntent() != IncidentIntent.RESOLVE) {
      result.append(value.getErrorType()).append(" ").append(value.getErrorMessage()).append(", ");

      if (value.getJobKey() != -1) {
        result.append("joBKey: ").append(shortenKey(value.getJobKey())).append(" ");
      }

      result
          .append(summarizeElementInformation(value.getElementId(), value.getElementInstanceKey()))
          .append(
              summarizeProcessInformation(value.getBpmnProcessId(), value.getProcessInstanceKey()));
    } else {
      result.append(shortenKey(record.getKey()));
    }
    return result.toString();
  }

  private String summarizeJob(final Record<?> record) {
    final var value = (JobRecordValue) record.getValue();

    return summarizeJobRecordValue(-1L, value);
  }

  private String summarizeJobRecordValue(final long jobKey, final JobRecordValue value) {
    final var result = new StringBuilder();

    if (jobKey != -1) {
      result.append(shortenKey(jobKey)).append(" ");
    }
    if (!StringUtils.isEmpty(value.getType())) {
      result
          .append("\"")
          .append(StringUtils.abbreviateMiddle(value.getType(), "..", 10))
          .append("\"")
          .append(summarizeElementInformation(value.getElementId(), value.getElementInstanceKey()));
    }

    if (value.getJobKind() != null && value.getJobKind() != JobKind.BPMN_ELEMENT) {
      result
          .append(" (")
          .append(abbreviate(value.getJobKind().toString()))
          .append("[")
          .append(abbreviate(value.getJobListenerEventType().toString()))
          .append("]),");
    }

    result.append(" ").append(value.getRetries()).append(" retries,");

    if (!StringUtils.isEmpty(value.getErrorCode())) {
      result.append(" ").append(value.getErrorCode()).append(":").append(value.getErrorMessage());
    }

    result.append(summarizeCustomHeaders(value.getCustomHeaders())).append(formatVariables(value));

    return result.toString();
  }

  private String summarizeCustomHeaders(final Map<String, String> customHeaders) {
    if (customHeaders.isEmpty()) {
      return "";
    }
    return " [%s]"
        .formatted(
            customHeaders.entrySet().stream()
                .map(this::abbreviateEntry)
                .map(e -> "%s:'%s'".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(",")));
  }

  private Entry<String, String> abbreviateEntry(final Entry<String, String> entry) {
    var key = entry.getKey();
    var value = entry.getValue();
    if (key.toLowerCase().contains("key")) {
      final long longOrNegative = getLongOrNegative(value);
      if (longOrNegative > 0) {
        value = shortenKey(longOrNegative);
      }
    }
    if (key.equals(Protocol.USER_TASK_CHANGED_ATTRIBUTES_HEADER_NAME)
        && value.length() > 2
        && value.startsWith("[")
        && value.endsWith("]")) {
      if (objectMapper == null) {
        // lazy load object mapper
        objectMapper = new ObjectMapper();
      }
      List<String> changedAttributes = null;
      try {
        changedAttributes = objectMapper.readValue(value, new TypeReference<>() {});
      } catch (final JsonProcessingException e) {
        LOG.warn("Failed to parse changed attributes '{}' as json list", value);
      }
      if (changedAttributes != null) {
        value =
            changedAttributes.stream()
                .map(CompactRecordLogger::abbreviateToFirstLetters)
                .collect(Collectors.joining(",", "[", "]"));
      }
    }

    if (key.startsWith("io.camunda.zeebe:")) {
      key = key.replace("io.camunda.zeebe:", "");
      key = abbreviateToFirstLetters(key);
    }
    key = StringUtils.abbreviateMiddle(key, "..", 10);
    value = StringUtils.abbreviateMiddle(value, "..", 10);
    return entry(key, value);
  }

  /** Returns only the first letter and any each capital letter. For example, userTaskKey -> uTK */
  private static String abbreviateToFirstLetters(final String toAbbreviate) {
    return toAbbreviate.replaceAll("(?<!^)[a-z]", "");
  }

  private String summarizeJobBatch(final Record<?> record) {
    final var value = (JobBatchRecordValue) record.getValue();
    final var jobKeys = value.getJobKeys();

    final var result = new StringBuilder();

    result.append("\"").append(value.getType()).append("\" ");
    if (jobKeys != null && !jobKeys.isEmpty()) {
      result.append(jobKeys.size()).append("/").append(value.getMaxJobsToActivate());
    } else {
      result.append("max: ").append(value.getMaxJobsToActivate());
    }

    if (value.isTruncated()) {
      result.append(" (truncated)");
    }

    if (jobKeys != null && !jobKeys.isEmpty()) {
      for (int i = 0; i < jobKeys.size(); i++) {
        final var jobKey = jobKeys.get(i);
        final var job = value.getJobs().get(i);

        result
            .append(StringUtils.rightPad("\n", 8 + valueTypeChars))
            .append(summarizeJobRecordValue(jobKey, job));
      }
    }

    return result.toString();
  }

  private String summarizeMessage(final Record<?> record) {
    final var value = (MessageRecordValue) record.getValue();

    final var result = new StringBuilder().append("\"").append(value.getName()).append("\"");

    if (!StringUtils.isEmpty(value.getCorrelationKey())) {
      result.append(" correlationKey: ").append(value.getCorrelationKey());
    }

    result.append(formatVariables(value));

    return result.toString();
  }

  private String summarizeMessageBatch(final Record<?> record) {
    final var value = (MessageBatchRecordValue) record.getValue();

    final var result =
        new StringBuilder()
            .append("\"")
            .append("messageKeys:")
            .append("\" ")
            .append(value.getMessageKeys())
            .append("\"");

    return result.toString();
  }

  private String summarizeMessageStartEventSubscription(final Record<?> record) {
    final var value = (MessageStartEventSubscriptionRecordValue) record.getValue();

    return new StringBuilder()
        .append("\"")
        .append(value.getMessageName())
        .append("\"")
        .append(" starting <process ")
        .append(formatId(value.getBpmnProcessId()))
        .append(formatVariables(value))
        .toString();
  }

  private String summarizeMessageSubscription(final Record<?> record) {
    final var value = (MessageSubscriptionRecordValue) record.getValue();

    final var result =
        new StringBuilder().append("\"").append(value.getMessageName()).append("\" ");

    if (value.isInterrupting()) {
      result.append("(inter.) ");
    }

    if (!StringUtils.isEmpty(value.getCorrelationKey())) {
      result.append("correlationKey: ").append(value.getCorrelationKey()).append(" ");
    }

    result
        .append("@[")
        .append(shortenKey(value.getElementInstanceKey()))
        .append("]")
        .append(" msg[")
        .append(shortenKey(value.getMessageKey()))
        .append("]")
        .append(
            summarizeProcessInformation(value.getBpmnProcessId(), value.getProcessInstanceKey()))
        .append(formatVariables(value));
    return result.toString();
  }

  private String summarizeProcessInstance(final Record<?> record) {
    final var value = (ProcessInstanceRecordValue) record.getValue();
    return new StringBuilder()
        .append(value.getBpmnElementType())
        .append(" ")
        .append(formatId(value.getElementId()))
        .append(
            summarizeProcessInformation(value.getBpmnProcessId(), value.getProcessInstanceKey()))
        .append(summarizeTreePath(value))
        .toString();
  }

  private String summarizeProcessInstanceBatch(final Record<?> record) {
    final var value = (ProcessInstanceBatchRecordValue) record.getValue();
    final var elementKey = value.getBatchElementInstanceKey();
    final var processKey = value.getProcessInstanceKey();
    final var result = new StringBuilder();

    result.append("idx:").append(value.getIndex());
    result.append(" PI:").append(shortenKey(processKey));
    if (elementKey != processKey) {
      result.append(" EI:").append(shortenKey(elementKey));
    }

    return result.append(formatTenant(value)).toString();
  }

  private String summarizeTreePath(final ProcessInstanceRecordValue value) {
    final StringBuilder result = new StringBuilder();
    if (!value.getElementInstancePath().isEmpty()) {
      result
          .append(" EI:")
          .append(
              value.getElementInstancePath().stream()
                  .map(p -> p.stream().map(this::shortenKey).collect(Collectors.joining("->")))
                  .toList());
    }
    if (!value.getProcessDefinitionPath().isEmpty()) {
      result
          .append(" PD:[")
          .append(
              value.getProcessDefinitionPath().stream()
                  .map(this::shortenKey)
                  .collect(Collectors.joining("->")))
          .append("]");
    }
    if (!value.getCallingElementPath().isEmpty()) {
      result.append(" CE: ").append(value.getCallingElementPath());
    }
    return result.toString();
  }

  private String summarizeProcessInstanceCreation(final Record<?> record) {
    final var value = (ProcessInstanceCreationRecordValue) record.getValue();
    return new StringBuilder()
        .append("new <process ")
        .append(formatId(value.getBpmnProcessId()))
        .append(">")
        .append(summarizeStartInstructions(value.getStartInstructions()))
        .append(formatVariables(value))
        .toString();
  }

  private String summarizeStartInstructions(
      final List<ProcessInstanceCreationStartInstructionValue> startInstructions) {
    if (startInstructions.isEmpty()) {
      return " (default start) ";
    } else {
      return startInstructions.stream()
          .map(ProcessInstanceCreationStartInstructionValue::getElementId)
          .collect(Collectors.joining(", ", " (starting before elements: ", ") "));
    }
  }

  private String summarizeProcessInstanceModification(final Record<?> record) {
    final var value = (ProcessInstanceModificationRecordValue) record.getValue();
    return new StringBuilder()
        .append(summarizeActivateInstructions(value.getActivateInstructions()))
        .append(summarizeTerminateInstructions(value.getTerminateInstructions()))
        .append(summarizeMoveInstructions(value.getMoveInstructions()))
        .toString();
  }

  private String summarizeActivateInstructions(
      final List<ProcessInstanceModificationActivateInstructionValue> activateInstructions) {
    if (activateInstructions.isEmpty()) {
      return "";
    }
    return activateInstructions.stream()
        .map(
            a ->
                "activate "
                    + formatId(a.getElementId())
                    + " "
                    + summarizeVariableInstructions(a.getVariableInstructions()))
        .collect(Collectors.joining("> <", "<", "> "));
  }

  private String summarizeVariableInstructions(
      final List<ProcessInstanceModificationVariableInstructionValue> variableInstructions) {
    if (variableInstructions.isEmpty()) {
      return "no vars";
    }
    final var builder = new StringBuilder().append("with vars ");
    variableInstructions.forEach(
        v -> {
          if (v.getElementId() != null) {
            builder.append("@").append(formatId(v.getElementId()));
          }
          builder.append(v.getVariables());
        });
    return builder.toString();
  }

  private String summarizeTerminateInstructions(
      final List<ProcessInstanceModificationTerminateInstructionValue> terminateInstructions) {
    if (terminateInstructions.isEmpty()) {
      return "";
    }
    return terminateInstructions.stream()
        .map(
            t ->
                "terminate [%s]"
                    .formatted(
                        t.getElementInstanceKey() > 0
                            ? shortenKey(t.getElementInstanceKey())
                            : t.getElementId()))
        .collect(Collectors.joining("> <", "<", "> "));
  }

  private String summarizeMoveInstructions(
      final List<ProcessInstanceModificationMoveInstructionValue> moveInstructions) {
    if (moveInstructions.isEmpty()) {
      return "";
    }
    return moveInstructions.stream()
        .map(t -> "move [%s to %s]".formatted(t.getSourceElementId(), t.getTargetElementId()))
        .collect(Collectors.joining("> <", "<", "> "));
  }

  private String summarizeProcessInstanceSubscription(final Record<?> record) {
    final var value = (ProcessMessageSubscriptionRecordValue) record.getValue();

    final var result =
        new StringBuilder().append("\"").append(value.getMessageName()).append("\" ");

    if (value.isInterrupting()) {
      result.append("(inter.) ");
    }

    if (!StringUtils.isEmpty(value.getCorrelationKey())) {
      result.append("correlationKey: ").append(value.getCorrelationKey()).append(" ");
    }

    result
        .append("@[")
        .append(shortenKey(value.getElementInstanceKey()))
        .append("]")
        .append(
            summarizeProcessInformation(value.getBpmnProcessId(), value.getProcessInstanceKey()))
        .append(formatVariables(value))
        .append(formatTenant(value));

    return result.toString();
  }

  private String summarizeAdHocSubProcessInstruction(final Record<?> record) {
    final var value = (AdHocSubProcessInstructionRecordValue) record.getValue();

    final var builder = new StringBuilder();
    builder
        .append(String.format("ACTIVATE elements %s", value.getActivateElements()))
        .append(
            String.format(
                " in ad-hoc sub-process [%s]", shortenKey(value.getAdHocSubProcessInstanceKey())));
    return builder.toString();
  }

  private String summarizeVariable(final Record<?> record) {
    final var value = (VariableRecordValue) record.getValue();

    final var builder = new StringBuilder();
    builder
        .append(String.format("%s->%s", value.getName(), formatVariableValue(value.getValue())))
        .append(String.format(" in <process [%s]", shortenKey(value.getProcessInstanceKey())));
    if (value.getProcessInstanceKey() != value.getScopeKey()) {
      // only add if they're different, no need to state things twice
      builder.append(String.format(" at [%s]", shortenKey(value.getScopeKey())));
    }
    return builder.append(">").toString();
  }

  private String summarizeVariableDocument(final Record<?> record) {
    final var value = (VariableDocumentRecordValue) record.getValue();

    return "[%s] in <scope [%s]>%s%s"
        .formatted(
            value.getUpdateSemantics(),
            shortenKey(value.getScopeKey()),
            formatVariables(value),
            formatTenant(value));
  }

  private String summarizeClusterVariable(final Record<?> record) {
    final var value = (ClusterVariableRecordValue) record.getValue();
    return "%s->%s%s"
        .formatted(value.getName(), formatVariableValue(value.getValue()), formatTenant(value));
  }

  private String summarizeConditionalSubscription(final Record<?> record) {
    final var value = (ConditionalSubscriptionRecordValue) record.getValue();

    return new StringBuilder()
        .append("\"")
        .append(value.getCondition())
        .append("\" \"")
        .append(String.join(", ", value.getVariableNames()))
        .append("\" \"")
        .append(String.join(", ", value.getVariableEvents()))
        .append("\" ")
        .append(" <catch event ")
        .append(formatId(value.getCatchEventId()))
        .append(formatKey(value.getElementInstanceKey()))
        .append(">")
        .toString();
  }

  private String summarizeConditionalEvaluation(final Record<?> record) {
    final var value = (ConditionalEvaluationRecordValue) record.getValue();
    final var result = new StringBuilder();

    if (value.getProcessDefinitionKey() > 0) {
      result
          .append("<process definition [")
          .append(shortenKey(value.getProcessDefinitionKey()))
          .append("]>");
    } else {
      result.append("(all process definitions)");
    }

    result.append(formatVariables(value)).append(formatTenant(value));

    // Add started process instances if any
    if (!value.getStartedProcessInstances().isEmpty()) {
      result.append(" -> started: [");
      result.append(
          value.getStartedProcessInstances().stream()
              .map(
                  instance ->
                      shortenKey(instance.getProcessDefinitionKey())
                          + ":"
                          + shortenKey(instance.getProcessInstanceKey()))
              .collect(Collectors.joining(", ")));
      result.append("]");
    }

    return result.toString();
  }

  private String summarizeExpression(final Record<?> record) {
    final var value = (ExpressionRecordValue) record.getValue();
    return "\""
        + StringUtils.abbreviate(value.getExpression(), "..", 50)
        + "\" = "
        + formatVariableValue(value.getResultValue())
        + formatTenant(value);
  }

  private StringBuilder summarizeRejection(final Record<?> record) {
    return new StringBuilder()
        .append("!")
        .append(record.getRejectionType())
        .append(" (")
        .append(StringUtils.abbreviate(record.getRejectionReason(), "..", 500))
        .append(")");
  }

  private String summarizeTimer(final Record<?> record) {
    final var value = (TimerRecordValue) record.getValue();
    final var builder = new StringBuilder();
    final var dueTime = Instant.ofEpochMilli(value.getDueDate()).atZone(ZoneId.systemDefault());

    builder
        .append(
            summarizeElementInformation(value.getTargetElementId(), value.getElementInstanceKey()))
        .append(" ")
        .append(
            summarizeProcessInformation(
                shortenKey(value.getProcessDefinitionKey()), value.getProcessInstanceKey()))
        .append(" due ")
        .append(shortenDateTime(dueTime));

    if (value.getRepetitions() > 1) {
      builder.append(value.getRepetitions()).append(" reps");
    }

    return builder.toString();
  }

  private String summarizeError(final Record<?> record) {
    final var value = (ErrorRecordValue) record.getValue();
    return new StringBuilder()
        .append("\"")
        .append(value.getExceptionMessage())
        .append("\"")
        .append(" ")
        .append(summarizeProcessInformation(null, value.getProcessInstanceKey()))
        .append(" (")
        .append(StringUtils.abbreviate(value.getStacktrace(), "..", 100))
        .append(")")
        .toString();
  }

  private String summarizeProcessEvent(final Record<?> record) {
    final ProcessEventRecordValue value = (ProcessEventRecordValue) record.getValue();
    return summarizeElementInformation(value.getTargetElementId(), value.getScopeKey())
        + summarizeProcessInformation(
            value.getProcessDefinitionKey(), value.getProcessInstanceKey())
        + formatVariables(value);
  }

  private String summarizeDecisionRequirements(final Record<?> record) {
    final var value = (DecisionRequirementsRecordValue) record.getValue();
    return summarizeDeploymentMetadata(
            value,
            value.getResourceName(),
            value.getDecisionRequirementsId(),
            value.getDecisionRequirementsKey(),
            value.getDecisionRequirementsVersion(),
            null,
            value.isDuplicate())
        .toString();
  }

  private String summarizeDecision(final Record<?> record) {
    final var value = (DecisionRecordValue) record.getValue();
    return String.format(
        "%s (version:%d) of <drg %s[%s]>",
        formatId(value.getDecisionId()),
        value.getVersion(),
        formatId(value.getDecisionRequirementsId()),
        shortenKey(value.getDecisionRequirementsKey()));
  }

  private String summarizeDecisionEvaluation(final Record<?> record) {
    final var value = (DecisionEvaluationRecordValue) record.getValue();

    final var summary = new StringBuilder();

    // On failure, show error message
    if (record.getIntent() == DecisionEvaluationIntent.FAILED) {
      // Show which decision failed (could be a required decision of the one being evaluated)
      summary.append("Error in <decision ").append(formatId(value.getFailedDecisionId()));
      if (value.getFailedDecisionId().equals(value.getDecisionId())) {
        summary.append("[").append(formatKey(value.getDecisionKey())).append("]>");
      } else {
        summary
            .append("> while evaluating <decision ")
            .append(formatId(value.getDecisionId()))
            .append("[")
            .append(formatKey(value.getDecisionKey()))
            .append("]>");
      }

      // Add input variables to facilitate debugging
      summary.append(formatVariables(value));

      // Add failure message
      summary.append(": \"").append(value.getEvaluationFailureMessage()).append("\"");
    } else { // On success, show decision input/output
      summary
          .append(value.getDecisionOutput())
          .append(summarizeDecisionInformation(value.getDecisionId(), value.getDecisionKey()))
          .append(formatVariables(value));
    }
    // In all cases, show variables and process/element information
    summary
        .append(
            summarizeProcessInformation(value.getBpmnProcessId(), value.getProcessInstanceKey()))
        .append(summarizeElementInformation(value.getElementId(), value.getElementInstanceKey()));
    return summary.toString();
  }

  private String summarizeDecisionInformation(final String decisionId, final long decisionKey) {
    return String.format(" of <decision %s[%s]>", formatId(decisionId), formatKey(decisionKey));
  }

  private String summarizeSignal(final Record<?> record) {
    final var value = (SignalRecordValue) record.getValue();

    return new StringBuilder()
        .append("\"")
        .append(value.getSignalName())
        .append("\"")
        .append(formatVariables(value))
        .toString();
  }

  private String summarizeSignalSubscription(final Record<?> record) {
    final var value = (SignalSubscriptionRecordValue) record.getValue();

    return new StringBuilder()
        .append("\"")
        .append(value.getSignalName())
        .append("\"")
        .append(" <process ")
        .append(formatId(value.getBpmnProcessId()))
        .toString();
  }

  private String summarizeUserTask(final Record<?> record) {
    final var value = (UserTaskRecordValue) record.getValue();
    final var result = new StringBuilder("task");

    if (StringUtils.isNotEmpty(value.getElementId())) {
      result.append(
          summarizeElementInformation(value.getElementId(), value.getElementInstanceKey()));
    }

    if (value.getChangedAttributes() != null && !value.getChangedAttributes().isEmpty()) {
      result
          .append(" changedAttributes")
          .append(" \"")
          .append(
              value.getChangedAttributes().stream()
                  .map(CompactRecordLogger::abbreviateToFirstLetters)
                  .toList())
          .append("\"");
    }
    addIfNotEmpty(result, value.getAssignee(), " assignee");
    addIfNotEmpty(result, value.getCandidateUsersList(), " candidateUsersList");
    addIfNotEmpty(result, value.getCandidateGroupsList(), " candidateGroupsList");
    addIfNotEmpty(result, value.getDueDate(), " dueDate");
    addIfNotEmpty(result, value.getFollowUpDate(), " followUpDate");
    result.append(" priority").append(" '").append(value.getPriority()).append("'");
    addIfNotEmpty(result, value.getAction(), " action");

    if (value.getFormKey() != -1) {
      result.append(" with <form ").append(shortenKey(value.getFormKey())).append(">");
    }

    if (StringUtils.isNotEmpty(value.getBpmnProcessId())) {
      result.append(
          summarizeProcessInformation(value.getBpmnProcessId(), value.getProcessInstanceKey()));
    }

    result.append(formatVariables(value));

    return result.toString();
  }

  private String summarizeCommandDistribution(final Record<?> record) {
    final var value = (CommandDistributionRecordValue) record.getValue();

    final StringBuilder stringBuilder =
        new StringBuilder()
            .append(value.getValueType())
            .append(" ")
            .append(value.getIntent())
            .append(" ");

    final var intent = (CommandDistributionIntent) record.getIntent();
    final var targetPartitionWord =
        switch (intent) {
          case STARTED, FINISH, FINISHED, CONTINUATION_REQUESTED, CONTINUE, CONTINUED -> "on";
          case DISTRIBUTING, ENQUEUED -> "to";
          case ACKNOWLEDGE, ACKNOWLEDGED -> "for";
        };

    return stringBuilder
        .append(
            "%s partition %d on queue %s"
                .formatted(targetPartitionWord, value.getPartitionId(), value.getQueueId()))
        .toString();
  }

  private String summarizeMessageCorrelation(final Record<?> record) {
    final var value = (MessageCorrelationRecordValue) record.getValue();
    final var correlationKey = value.getCorrelationKey();

    final var result = new StringBuilder().append("\"").append(value.getName()).append("\"");

    if (correlationKey != null && !correlationKey.isEmpty()) {
      result.append(" correlationKey: ").append(correlationKey);
    }

    result
        .append(" processInstanceKey: ")
        .append(value.getProcessInstanceKey())
        .append(formatVariables(value));

    return result.toString();
  }

  private String summarizeClock(final Record<?> record) {
    final var value = (ClockRecordValue) record.getValue();

    final var clockValue =
        switch (record.getIntent()) {
          case ClockIntent.PIN, ClockIntent.PINNED -> formatPinnedTime(value.getTime());
          case ClockIntent.RESET, ClockIntent.RESETTED -> "system time";
          default -> value.getTime();
        };

    return "to %s".formatted(clockValue);
  }

  private String summarizeRole(final Record<?> record) {
    final var value = (RoleRecordValue) record.getValue();
    return summarizeRole(value);
  }

  private String summarizeRole(final RoleRecordValue value) {
    final StringJoiner joiner = new StringJoiner(", ", "Role[", "]");

    if (value.getRoleKey() != -1) {
      joiner.add("Key=" + shortenKey(value.getRoleKey()));
    }

    if (StringUtils.isNotBlank(value.getRoleId())) {
      joiner.add("Id=" + formatId(value.getRoleId()));
    }

    if (StringUtils.isNotBlank(value.getName())) {
      joiner.add("Name=" + formatId(value.getName()));
    }

    if (StringUtils.isNotBlank(value.getDescription())) {
      joiner.add("Description=" + value.getDescription());
    }

    if (StringUtils.isNotBlank(value.getEntityId())) {
      joiner.add("EntityId=" + formatId(value.getEntityId()));
    }

    return joiner.toString();
  }

  private String summarizeTenant(final Record<?> record) {
    final var value = (TenantRecordValue) record.getValue();
    return summarizeTenant(value);
  }

  private String summarizeTenant(final TenantRecordValue value) {
    final StringJoiner joiner = new StringJoiner(", ", "Tenant[", "]");

    if (value.getTenantKey() != -1) {
      joiner.add("Key=" + shortenKey(value.getTenantKey()));
    }

    if (StringUtils.isNotBlank(value.getTenantId())) {
      joiner.add("Id=" + formatId(value.getTenantId()));
    }

    if (StringUtils.isNotBlank(value.getName())) {
      joiner.add("Name=" + formatId(value.getName()));
    }

    if (StringUtils.isNotBlank(value.getEntityId())) {
      joiner.add("EntityId=" + formatId(value.getEntityId()));
    }
    return joiner.toString();
  }

  private String summarizeGroup(final Record<?> record) {
    final var value = (GroupRecordValue) record.getValue();

    final StringJoiner joiner = new StringJoiner(", ", "Group[", "]");

    if (value.getGroupKey() != -1) {
      joiner.add("Key=" + shortenKey(value.getGroupKey()));
    }

    if (StringUtils.isNotBlank(value.getGroupId())) {
      joiner.add("Id=" + formatId(value.getGroupId()));
    }

    if (StringUtils.isNotBlank(value.getName())) {
      joiner.add("Name=" + formatId(value.getName()));
    }

    if (StringUtils.isNotBlank(value.getEntityId())) {
      joiner.add("EntityKey=" + formatId(value.getEntityId()));
    }

    if (StringUtils.isNotBlank(value.getEntityId())) {
      joiner.add("EntityType=" + value.getEntityType());
    }

    return joiner.toString();
  }

  private String summarizeMappingRule(final Record<?> record) {
    final var value = (MappingRuleRecordValue) record.getValue();
    return summarizeMappingRule(value);
  }

  private String summarizeMappingRule(final MappingRuleRecordValue value) {

    final StringJoiner joiner = new StringJoiner(", ", "MappingRule[", "]");

    if (value.getMappingRuleKey() != -1) {
      joiner.add("Key=" + shortenKey(value.getMappingRuleKey()));
    }

    if (StringUtils.isNotBlank(value.getMappingRuleId())) {
      joiner.add("mappingRuleId=" + formatId(value.getMappingRuleId()));
    }

    if (StringUtils.isNotBlank(value.getClaimName())) {
      joiner.add("claimName=" + value.getClaimName());
    }

    if (StringUtils.isNotBlank(value.getClaimValue())) {
      joiner.add("claimValue=" + value.getClaimValue());
    }

    return joiner.toString();
  }

  private String summarizeUser(final Record<?> record) {
    final var value = (UserRecordValue) record.getValue();
    return summarizeUser(value, record.getKey() != value.getUserKey());
  }

  private String summarizeUser(final UserRecordValue value) {
    return summarizeUser(value, true);
  }

  private String summarizeUser(final UserRecordValue value, final boolean includeUserKey) {
    final StringBuilder builder = new StringBuilder();
    if (includeUserKey) {
      builder.append(shortenKey(value.getUserKey())).append(" ");
    }
    builder
        .append("u=")
        .append(formatId(value.getUsername()))
        .append(" @=")
        .append(value.getEmail())
        .append(" n=")
        .append(value.getName());

    return builder.toString();
  }

  private String summarizeAuthorization(final Record<?> record) {
    final var value = (AuthorizationRecordValue) record.getValue();
    return summarizeAuthorization(value);
  }

  private String summarizeAuthorization(final AuthorizationRecordValue value) {
    final String resourceIdentifier =
        value.getResourceMatcher() == AuthorizationResourceMatcher.PROPERTY
            ? "based on \"%s\" property".formatted(value.getResourcePropertyName())
            : formatId(value.getResourceId());

    return "%s %s can %s %s %s"
        .formatted(
            value.getOwnerType(),
            formatId(value.getOwnerId()),
            value.getPermissionTypes(),
            value.getResourceType(),
            resourceIdentifier);
  }

  private String summarizeAsyncRequest(final Record<?> record) {
    final var value = (AsyncRequestRecordValue) record.getValue();

    return new StringBuilder()
        .append("req ")
        .append(abbreviate(value.getValueType().name()))
        .append(":")
        .append(abbreviate(value.getIntent().name()))
        .append(" at [")
        .append(shortenKey(value.getScopeKey()))
        .append("]")
        .toString();
  }

  private String summarizeResource(final Record<?> record) {
    final var value = (Resource) record.getValue();
    return summarizeDeploymentMetadata(
            value,
            value.getResourceName(),
            value.getResourceId(),
            value.getResourceKey(),
            value.getVersion(),
            value.getVersionTag(),
            value.isDuplicate())
        .toString();
  }

  private String summarizeResourceDeletion(final Record<?> record) {
    final var value = (ResourceDeletionRecordValue) record.getValue();
    return "res:%s%s".formatted(shortenKey(value.getResourceKey()), formatTenant(value));
  }

  private String summarizeMultiInstance(final Record<?> record) {
    final var value = (MultiInstanceRecordValue) record.getValue();
    return new StringBuilder()
        .append("inputCollection: ")
        .append(value.getInputCollection())
        .toString();
  }

  private String summarizeRuntimeInstruction(final Record<?> record) {
    final var value = (RuntimeInstructionRecordValue) record.getValue();

    final var result = new StringBuilder();

    final var formattedInstanceKey =
        value.getProcessInstanceKey() < 0 ? "?" : shortenKey(value.getProcessInstanceKey());
    result.append(String.format("[%s]", formattedInstanceKey));

    if (value.getElementId() != null) {
      result.append(" interrupted by \"").append(value.getElementId()).append("\"");
    }
    return result.toString();
  }

  protected String summarizeUsageMetrics(final Record<?> record) {
    final var result = new StringBuilder();
    final var value = (UsageMetricRecordValue) record.getValue();
    result
        .append(value.getEventType())
        .append(":")
        .append(value.getIntervalType())
        .append(" ")
        .append(formatTime("start", value.getStartTime()))
        .append(" ")
        .append(formatTime("end", value.getEndTime()))
        .append(" ")
        .append(formatTime("reset", value.getResetTime()))
        .append(formatVariables(toMetricValueMap(value.getCounterValues(), value.getSetValues())));

    return result.toString();
  }

  private String summarizeCompensationSubscription(final Record<?> record) {
    final var result = new StringBuilder();
    final var value = (CompensationSubscriptionRecordValue) record.getValue();
    if (value.getThrowEventInstanceKey() < 0) {
      // compensation subscription has not been triggered yet, for example:
      // E COMP_SUB CREATED #28->#22 K11 "CompHandler" → "TaskToCompensate"[K08] in <process
      // K03[K04]>
      // explains that the compensation handler is registered for the compensable activity
      result
          .append("\"")
          .append(StringUtils.abbreviateMiddle(value.getCompensationHandlerId(), "..", 20))
          .append("\"")
          .append("[")
          .append(shortenKey(value.getCompensationHandlerInstanceKey()))
          .append("]")
          .append(" → ")
          .append("\"")
          .append(StringUtils.abbreviateMiddle(value.getCompensableActivityId(), "..", 20))
          .append("\"")
          .append("[")
          .append(shortenKey(value.getCompensableActivityInstanceKey()))
          .append("]")
          .append(
              summarizeProcessInformation(
                  value.getProcessDefinitionKey(), value.getProcessInstanceKey()));
    } else {
      // an event was thrown triggering the compensation, for example:
      // E COMP_SUB TRIGGERED #39->#22 K11 "CompThrowEvent"[K13] → "CompensationHandler"[K15] (no
      // vars)
      // explains that the throw event has triggered the compensation handler without vars
      result
          .append("\"")
          .append(StringUtils.abbreviateMiddle(value.getThrowEventId(), "..", 20))
          .append("\"")
          .append("[")
          .append(shortenKey(value.getThrowEventInstanceKey()))
          .append("]")
          .append(" → ")
          .append("\"")
          .append(StringUtils.abbreviateMiddle(value.getCompensationHandlerId(), "..", 20))
          .append("\"")
          .append("[")
          .append(shortenKey(value.getCompensationHandlerInstanceKey()))
          .append("]")
          .append(formatVariables(value));
    }

    result.append(formatTenant(value));
    return result.toString();
  }

  private String summarizeBatchOperationCreation(final Record<?> record) {
    final var value = (BatchOperationCreationRecordValue) record.getValue();

    final var sb = new StringBuilder().append("type=").append(value.getBatchOperationType());
    if (value.getMigrationPlan() != null
        && value.getMigrationPlan().getMappingInstructions() != null
        && !value.getMigrationPlan().getMappingInstructions().isEmpty()) {
      sb.append(", migrationPlan=").append(value.getMigrationPlan());
    }
    if (value.getModificationPlan() != null
        && value.getModificationPlan().getMoveInstructions() != null
        && !value.getModificationPlan().getMoveInstructions().isEmpty()) {
      sb.append(", modificationPlan=").append(value.getModificationPlan());
    }
    return sb.toString();
  }

  private String summarizeBatchOperationChunk(final Record<?> record) {
    final var value = (BatchOperationChunkRecordValue) record.getValue();

    return new StringBuilder().append("numItems=").append(value.getItems().size()).toString();
  }

  private String summarizeBatchOperationInitialization(final Record<?> record) {
    final var value = (BatchOperationInitializationRecordValue) record.getValue();

    return new StringBuilder()
        .append("cursor=")
        .append(value.getSearchResultCursor())
        .append(", pageSize=")
        .append(value.getSearchQueryPageSize())
        .toString();
  }

  private String summarizeBatchOperationExecution(final Record<?> record) {
    final var value = (BatchOperationExecutionRecordValue) record.getValue();

    final var sb = new StringBuilder();
    if (value.getItemKeys() != null && !value.getItemKeys().isEmpty()) {
      sb.append("items=").append(value.getItemKeys());
    }
    return sb.toString();
  }

  private String summarizeBatchOperationPartitionLifecycle(final Record<?> record) {
    final var value = (BatchOperationPartitionLifecycleRecordValue) record.getValue();

    return new StringBuilder()
        .append("sourcePartition=")
        .append(value.getSourcePartitionId())
        .toString();
  }

  private String summarizeBatchOperationLifecycle(final Record<?> record) {
    final var value = (BatchOperationLifecycleManagementRecordValue) record.getValue();

    final var sb = new StringBuilder();
    if (value.getErrors() != null && !value.getErrors().isEmpty()) {
      sb.append("errors=").append(value.getErrors().size());
    }

    return sb.toString();
  }

  private String summarizeProcessInstanceResult(final Record<?> record) {
    final var value = (ProcessInstanceResultRecordValue) record.getValue();
    return new StringBuilder()
        .append(
            summarizeProcessInformation(value.getBpmnProcessId(), value.getProcessInstanceKey()))
        .append(formatVariables(value))
        .toString();
  }

  private String summarizeEscalation(final Record<?> record) {
    final var value = (EscalationRecordValue) record.getValue();

    final StringBuilder summary = new StringBuilder();
    summary
        .append(formatId(value.getEscalationCode()))
        .append(" (thrown by ")
        .append(formatId(value.getThrowElementId()));
    if (StringUtils.isNotBlank(value.getCatchElementId())) {
      summary.append("; caught by ").append(formatId(value.getCatchElementId()));
    }
    summary.append(")");
    summary.append(summarizeProcessInformation(null, value.getProcessInstanceKey()));

    return summary.toString();
  }

  private String summarizeIdentitySetup(final Record<?> record) {
    final var value = (IdentitySetupRecordValue) record.getValue();

    final StringBuilder summary = new StringBuilder();

    if (!value.getRoles().isEmpty()) {
      summary.append(
          value.getRoles().stream()
              .map(this::summarizeRole)
              .collect(Collectors.joining(", ", "Roles: {", "}")));
    }

    if (!value.getRoleMembers().isEmpty()) {
      if (!summary.isEmpty()) {
        summary.append("; ");
      }
      summary.append(
          value.getRoleMembers().stream()
              .map(this::summarizeRole)
              .collect(Collectors.joining(", ", "Role members: {", "}")));
    }

    if (!value.getUsers().isEmpty()) {
      if (!summary.isEmpty()) {
        summary.append("; ");
      }
      summary.append(
          value.getUsers().stream()
              .map(this::summarizeUser)
              .collect(Collectors.joining(", ", "Users: {", "}")));
    }

    if (value.getDefaultTenant() != null) {
      if (!summary.isEmpty()) {
        summary.append("; ");
      }
      summary.append("Default tenant: ").append(summarizeTenant(value.getDefaultTenant()));
    }

    if (!value.getTenantMembers().isEmpty()) {
      if (!summary.isEmpty()) {
        summary.append("; ");
      }
      summary.append(
          value.getTenantMembers().stream()
              .map(this::summarizeTenant)
              .collect(Collectors.joining(", ", "Tenant members: {", "}")));
    }

    if (!value.getMappingRules().isEmpty()) {
      if (!summary.isEmpty()) {
        summary.append("; ");
      }
      summary.append(
          value.getMappingRules().stream()
              .map(this::summarizeMappingRule)
              .collect(Collectors.joining(", ", "Mapping rules: {", "}")));
    }

    if (!value.getAuthorizations().isEmpty()) {
      if (!summary.isEmpty()) {
        summary.append("; ");
      }
      summary.append(
          value.getAuthorizations().stream()
              .map(this::summarizeAuthorization)
              .collect(Collectors.joining(", ", "Authorizations: {", "}")));
    }

    return summary.toString();
  }

  private String summarizeScale(final Record<?> record) {
    final var value = (ScaleRecordValue) record.getValue();

    final StringBuilder summary = new StringBuilder();

    switch (record.getIntent()) {
      case ScaleIntent.SCALE_UP, ScaleIntent.SCALING_UP ->
          summary
              .append("Scale to ")
              .append(value.getDesiredPartitionCount())
              .append(" partitions");
      case ScaleIntent.STATUS -> {} // No information in status request command
      case ScaleIntent.STATUS_RESPONSE ->
          summary
              .append("Partitions: ")
              .append(value.getRedistributedPartitions())
              .append(" (")
              .append(value.getRedistributedPartitions().size())
              .append("/")
              .append(value.getDesiredPartitionCount())
              .append("; ")
              .append(value.getMessageCorrelationPartitions())
              .append(" in message correlation)");
      case ScaleIntent.MARK_PARTITION_BOOTSTRAPPED,
          ScaleIntent.PARTITION_BOOTSTRAPPED,
          ScaleIntent.SCALED_UP ->
          summary
              .append("Partition: ")
              .append(value.getRedistributedPartitions().iterator().next());
      default -> summarizeMiscValue(record);
    }

    return summary.toString();
  }

  private String summarizeProcessInstanceMigration(final Record<?> record) {
    final var value = (ProcessInstanceMigrationRecordValue) record.getValue();

    final StringBuilder summary = new StringBuilder();

    summary.append(shortenKey(value.getProcessInstanceKey()));
    summary.append("->");
    summary.append(shortenKey(value.getTargetProcessDefinitionKey()));

    // Only include mapping in first record in order to avoid cluttering the logs.
    // The MIGRATE record is referenced by the MIGRATED one so it's easy to find
    // it if needed.
    if (record.getIntent() == ProcessInstanceMigrationIntent.MIGRATE) {
      if (value.getMappingInstructions().isEmpty()) {
        summary.append(" (missing mapping)");
      } else {
        final StringJoiner mapping = new StringJoiner(", ", " (mapping: ", ")");
        for (final var instruction : value.getMappingInstructions()) {
          mapping.add(
              formatId(instruction.getSourceElementId())
                  + "->"
                  + formatId(instruction.getTargetElementId()));
        }
        summary.append(mapping);
      }
    }

    return summary.toString();
  }

  private String summarizeCheckpoint(final Record<?> record) {
    final var value = (CheckpointRecordValue) record.getValue();
    return "checkpoint %s @ #%s"
        .formatted(value.getCheckpointId(), formatPosition(value.getCheckpointPosition()));
  }

  private String summarizeHistoryDeletion(final Record<?> record) {
    final var value = (HistoryDeletionRecordValue) record.getValue();

    return "Resource key: "
        + shortenKey(value.getResourceKey())
        + ", Resource type: "
        + value.getResourceType();
  }

  private String summarizeGlobalListener(final Record<?> record) {
    final var value = (GlobalListenerRecordValue) record.getValue();
    return summarizeGlobalListener(value);
  }

  private String summarizeGlobalListener(final GlobalListenerRecordValue value) {
    return String.format(
        "%s<%s> x %s%s",
        formatId(value.getType()),
        value.getEventTypes().stream().collect(Collectors.joining(", ")),
        value.getRetries(),
        value.isAfterNonGlobal() ? " [after]" : "");
  }

  private String summarizeGlobalListenerBatch(final Record<?> record) {
    final var value = (GlobalListenerBatchRecordValue) record.getValue();
    final StringBuilder summary = new StringBuilder();
    if (record.getKey() != value.getGlobalListenerBatchKey()) {
      summary.append("key: ").append(shortenKey(value.getGlobalListenerBatchKey())).append(" ");
    }
    summary.append(
        value.getListeners().stream()
            .map(this::summarizeGlobalListener)
            .collect(Collectors.joining("; ", "taskListeners: {", "}")));
    return summary.toString();
  }

  private String summarizeForm(final Record<?> record) {
    final var value = (Form) record.getValue();
    return summarizeDeploymentMetadata(
            value,
            value.getResourceName(),
            value.getFormId(),
            value.getFormKey(),
            value.getVersion(),
            value.getVersionTag(),
            value.isDuplicate())
        .toString();
  }

  private StringBuilder summarizeDeploymentMetadata(
      final TenantOwned value,
      final String resourceName,
      final String id,
      final long key,
      final int version,
      final String versionTag,
      final boolean duplicate) {

    final StringBuilder result = new StringBuilder();

    result
        .append(resourceName)
        .append(" -> ")
        .append(formatId(id))
        .append("[")
        .append(shortenKey(key))
        .append("]")
        .append(" (version:")
        .append(version);

    if (StringUtils.isNotEmpty(versionTag)) {
      result.append(" tag:").append(versionTag);
    }

    result.append(")");

    if (duplicate) {
      result.append(" (dup)");
    }

    return result.append(formatTenant(value));
  }

  private String formatPinnedTime(final long time) {
    final var dateTime = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
    return "%s (timestamp: %d)".formatted(shortenDateTime(dateTime), time);
  }

  private String formatTime(final String name, final long time) {
    if (time == -1) {
      return "%s[%s]".formatted(name, -1);
    }
    final var dateTime = Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC);
    return "%s[%s]".formatted(name, shortenDateTime(dateTime));
  }

  private String formatTenant(final TenantOwned value) {
    final String tenantId = value.getTenantId();
    return StringUtils.isEmpty(tenantId) || TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)
        ? ""
        : " (tenant: %s)".formatted(tenantId);
  }

  private String formatVariables(final RecordValueWithVariables value) {
    final var variables = value.getVariables();
    if (variables == null || variables.isEmpty()) {
      return " (no vars)";
    }

    return " vars: "
        + variables.entrySet().stream()
            .sorted(Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + formatVariableValue(entry.getValue()))
            .collect(Collectors.joining(", ", "{", "}"));
  }

  private String formatVariables(final Map<String, Object> metricValues) {
    if (metricValues == null || metricValues.isEmpty()) {
      return " (no metricValues)";
    }
    return " metricValues: "
        + metricValues.entrySet().stream()
            .sorted(Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + formatVariableValue(entry.getValue()))
            .collect(Collectors.joining(", ", "{", "}"));
  }

  /**
   * Combines metric values from counters or sets into a generic map.
   *
   * <p>If {@code counterValues} is not empty, its entries are added to the result map. Otherwise,
   * {@code setValues} entries are added. Only one of the input maps is expected to be non-empty.
   *
   * @param counterValues a map of metric counters (String to Long)
   * @param setValues a map of metric sets (String to Set<Long>)
   * @return a generic map containing either counter or set values
   */
  public Map<String, Object> toMetricValueMap(
      final Map<String, Long> counterValues, final Map<String, Set<Long>> setValues) {
    final Map<String, Object> variables = new HashMap<>();
    if (!counterValues.isEmpty()) {
      variables.putAll(counterValues);
    } else {
      variables.putAll(setValues);
    }
    return variables;
  }

  /**
   * Formats a variable value for logging.
   *
   * <ul>
   *   <li>If the value is {@code null}, returns {@code "null"}.
   *   <li>If the value is a {@code String}, it is wrapped in double quotes and shortened to a
   *       maximum of 15 characters if necessary. The original string length is appended in the
   *       format {@code "..(len)"}.
   *   <li>All other types are converted to string without shortening or quotes.
   * </ul>
   *
   * @param value the variable value to format
   * @return a formatted string representation for logging
   */
  private String formatVariableValue(final Object value) {
    if (value == null) {
      return "null";
    }

    if (value instanceof String str) {
      int length = str.length();

      // strip outer quotes, if present
      if (length >= 2 && str.startsWith("\"") && str.endsWith("\"")) {
        str = str.substring(1, length - 1);
        length -= 2; // adjust length after stripping quotes
      }

      final int maxLength = 15;
      final var shortened =
          length > maxLength ? str.substring(0, maxLength) + "..(" + length + ")" : str;

      return "\"" + shortened + "\"";
    }

    return value.toString();
  }

  /**
   * Shortens and formats the key and stores it in the key substitutions, that is printed in the
   * list of decomposed keys for debugging at the end.
   *
   * @param input the key to shorten
   * @return the shortened key, e.g. {@code "K01"}
   */
  private String shortenKey(final long input) {
    return substitutions.computeIfAbsent(input, this::formatKey);
  }

  /**
   * Only formats the key. If you need a shortened key, you probably need {@link #shortenKey(long)}.
   *
   * <p>Formats the key to the format `[Pn]Km`, where:
   *
   * <ul>
   *   <li>P: means Partition (only added in case of multiPartition)
   *   <li>n: replaced with the partition id (only added in case of multiPartition)
   *   <li>K: means Key
   *   <li>m: the decoded key in the partition, leftpadded with '0's
   * </ul>
   *
   * @param key the key to format (should be encoded with a partition id)
   * @return the formatted key
   */
  private String formatKey(final long key) {
    final var result = new StringBuilder();

    if (multiPartition) {
      if (key > 0) {
        result.append("P").append(Protocol.decodePartitionId(key));
      } else {
        result.append("  ");
      }
    }

    if (key > 0) {
      result.append(
          "K" + leftPad(Long.toString(Protocol.decodeKeyInPartition(key)), keyDigits, '0'));
    } else {
      result.append(leftPad(Long.toString(key), keyDigits + 1, ' '));
    }

    return result.toString();
  }

  private String formatPosition(final long input) {
    if (input >= 0) {
      return "#" + leftPad(Long.toString(input), keyDigits, '0');
    } else {
      return leftPad(Long.toString(input), keyDigits + 1, ' ');
    }
  }

  private String formatId(final String input) {
    return "\"" + StringUtils.abbreviateMiddle(input, "..", 16) + "\"";
  }

  private String abbreviate(final String input) {
    String result = input;

    for (final Entry<String, String> entry : ABBREVIATIONS) {
      result = result.replace(entry.getKey(), entry.getValue());
    }

    return result;
  }

  // omit the date part if it's the same as right now
  private String shortenDateTime(final ZonedDateTime time) {
    final ZonedDateTime now = ZonedDateTime.now();
    final StringBuilder builder = new StringBuilder();

    if (!now.toLocalDate().isEqual(time.toLocalDate())) {
      builder.append(DateTimeFormatter.ISO_LOCAL_DATE.format(time));
    }

    builder.append("T").append(DateTimeFormatter.ISO_LOCAL_TIME.format(time));
    return builder.toString();
  }

  // add non-empty String elements
  private void addIfNotEmpty(final StringBuilder result, final String value, final String name) {
    if (StringUtils.isNotEmpty(value)) {
      result.append(name).append(" \"").append(value).append("\"");
    }
  }

  // add non-empty String list elements
  private void addIfNotEmpty(
      final StringBuilder result, final List<String> value, final String name) {
    if (value != null && !value.isEmpty()) {
      result.append(name).append(" \"").append(value).append("\"");
    }
  }

  private static long getLongOrNegative(final String value) {
    try {
      return Long.parseLong(value);
    } catch (final NumberFormatException e) {
      return -1;
    }
  }

  private String summarizeJobMetricsBatch(final Record<?> record) {
    final var value = (JobMetricsBatchRecordValue) record.getValue();
    final var jobMetrics = value.getJobMetrics();
    final var metricsCount = jobMetrics != null ? jobMetrics.size() : 0;

    return "metrics: " + metricsCount + (value.getRecordSizeLimitExceeded() ? " (truncated)" : "");
  }
}
