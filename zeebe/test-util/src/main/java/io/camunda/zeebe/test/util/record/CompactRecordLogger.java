/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessActivityActivationRecordValue;
import io.camunda.zeebe.protocol.record.value.ClockRecordValue;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ErrorRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageCorrelationRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessEventRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationStartInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationActivateInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationTerminateInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationVariableInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
          entry("COMPLETE_TASK_LISTENER", "COMP_TL"),
          entry("DENY_TASK_LISTENER", "DENY_TL"),
          entry("TASK_LISTENER", "TL"),
          entry("ASSIGNMENT_DENIED", "ASGN_DENIED"),
          entry("COMPLETION_DENIED", "COMP_DENIED"),
          entry("UPDATE_DENIED", "UPDT_DENIED"),
          entry("SEQUENCE_FLOW_TAKEN", "SQ_FLW_TKN"),
          entry("PROCESS_INSTANCE_CREATION", "CREA"),
          entry("PROCESS_INSTANCE_MODIFICATION", "MOD"),
          entry("PROCESS_INSTANCE", "PI"),
          entry("PROCESS", "PROC"),
          entry("AD_HOC_SUB_PROC_ACTIVITY_ACTIVATION", "AH_ACT"),
          entry("TIMER", "TIME"),
          entry("MESSAGE", "MSG"),
          entry("SUBSCRIPTION", "SUB"),
          entry("SEQUENCE", "SEQ"),
          entry("DEPLOYMENT_DISTRIBUTION", "DPLY_DSTR"),
          entry("DEPLOYMENT", "DPLY"),
          entry("VARIABLE", "VAR"),
          entry("ELEMENT_", ""),
          entry("_ELEMENT", ""),
          entry("EVENT", "EVNT"),
          entry("DECISION_REQUIREMENTS", "DRG"),
          entry("EVALUATION", "EVAL"),
          entry("SIGNAL_SUBSCRIPTION", "SIG_SUBSCRIPTION"),
          entry("SIGNAL", "SIG"),
          entry("COMMAND_DISTRIBUTION", "DSTR"),
          entry("USER_TASK", "UT"),
          entry("ROLE", "RL"),
          entry("GROUP", "GR"),
          entry("MAPPING", "MAP"));

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
    valueLoggers.put(ValueType.PROCESS_INSTANCE_CREATION, this::summarizeProcessInstanceCreation);
    valueLoggers.put(
        ValueType.PROCESS_INSTANCE_MODIFICATION, this::summarizeProcessInstanceModification);
    valueLoggers.put(
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION, this::summarizeProcessInstanceSubscription);
    valueLoggers.put(
        ValueType.AD_HOC_SUB_PROCESS_ACTIVITY_ACTIVATION,
        this::summarizeAdHocSubProcessActivityActivation);
    valueLoggers.put(ValueType.VARIABLE, this::summarizeVariable);
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
    valueLoggers.put(ValueType.MAPPING, this::summarizeMapping);
  }

  public CompactRecordLogger(final Collection<Record<?>> records) {
    this.records = new ArrayList<>(records);
    multiPartition = isMultiPartition();
    hasTimerEvents = records.stream().anyMatch(r -> r.getValueType() == ValueType.TIMER);

    final var highestPosition = this.records.getLast().getPosition();

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
    return String.format(
        "%s -> %s (version:%d)",
        value.getResourceName(), formatId(value.getBpmnProcessId()), value.getVersion());
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

  private String summarizeVariables(final Map<String, Object> variables) {
    if (variables != null && !variables.isEmpty()) {
      return " with variables: " + variables;
    } else {
      return " (no vars)";
    }
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

    result
        .append(summarizeCustomHeaders(value.getCustomHeaders()))
        .append(summarizeVariables(value.getVariables()));

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

    result.append(summarizeVariables(value.getVariables()));

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
        .append(summarizeVariables(value.getVariables()))
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
        .append(summarizeVariables(value.getVariables()));
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
        .append(summarizeVariables(value.getVariables()))
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
        .map(t -> "terminate [%s]".formatted(shortenKey(t.getElementInstanceKey())))
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
        .append(summarizeVariables(value.getVariables()))
        .append(" (tenant: %s)".formatted(value.getTenantId()));

    return result.toString();
  }

  private String summarizeAdHocSubProcessActivityActivation(final Record<?> record) {
    final var value = (AdHocSubProcessActivityActivationRecordValue) record.getValue();

    final var builder = new StringBuilder();
    builder
        .append(String.format("ACTIVATE elements %s", value.getElements()))
        .append(
            String.format(
                " in adhoc subprocess [%s]",
                shortenKey(Long.parseLong(value.getAdHocSubProcessInstanceKey()))));
    return builder.toString();
  }

  private String summarizeVariable(final Record<?> record) {
    final var value = (VariableRecordValue) record.getValue();

    final var builder = new StringBuilder();
    builder
        .append(String.format("%s->%s", value.getName(), value.getValue()))
        .append(String.format(" in <process [%s]", shortenKey(value.getProcessInstanceKey())));
    if (value.getProcessInstanceKey() != value.getScopeKey()) {
      // only add if they're different, no need to state things twice
      builder.append(String.format(" at [%s]", shortenKey(value.getScopeKey())));
    }
    return builder.append(">").toString();
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
        + summarizeVariables(value.getVariables());
  }

  private String summarizeDecisionRequirements(final Record<?> record) {
    final var value = (DecisionRequirementsRecordValue) record.getValue();
    return String.format(
        "%s -> %s (version:%d)",
        value.getResourceName(),
        formatId(value.getDecisionRequirementsId()),
        value.getDecisionRequirementsVersion());
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
    return new StringBuilder()
        .append(value.getDecisionOutput())
        .append(summarizeDecisionInformation(value.getDecisionId(), value.getDecisionKey()))
        .append(summarizeVariables(value.getVariables()))
        .append(
            summarizeProcessInformation(value.getBpmnProcessId(), value.getProcessInstanceKey()))
        .append(summarizeElementInformation(value.getElementId(), value.getElementInstanceKey()))
        .toString();
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
        .append(summarizeVariables(value.getVariables()))
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

    result.append(summarizeVariables(value.getVariables()));

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
        .append(summarizeVariables(value.getVariables()));

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

    final StringBuilder builder = new StringBuilder("Role[");
    builder
        .append("Key=")
        .append(shortenKey(value.getRoleKey()))
        .append(", Id=")
        .append(formatId(value.getRoleId()))
        .append(", Name=")
        .append(formatId(value.getName()))
        .append(", EntityKey=")
        .append(shortenKey(value.getEntityKey()))
        .append("]");

    return builder.toString();
  }

  private String summarizeTenant(final Record<?> record) {
    final var value = (TenantRecordValue) record.getValue();

    final StringBuilder builder = new StringBuilder("Tenant[");
    builder
        .append("Key=")
        .append(shortenKey(value.getTenantKey()))
        .append(", Id=")
        .append(formatId(value.getTenantId()))
        .append(", Name=")
        .append(formatId(value.getName()))
        .append(", EntityId=")
        .append(formatId(value.getEntityId()))
        .append("]");

    return builder.toString();
  }

  private String summarizeGroup(final Record<?> record) {
    final var value = (GroupRecordValue) record.getValue();

    final StringBuilder builder = new StringBuilder("Group[");
    builder
        .append("Key=")
        .append(shortenKey(value.getGroupKey()))
        .append(", Id=")
        .append(formatId(value.getGroupId()))
        .append(", Name=")
        .append(formatId(value.getName()))
        .append(", EntityKey=")
        .append(formatId(value.getEntityId()))
        .append(", EntityType=")
        .append(value.getEntityType())
        .append("]");

    return builder.toString();
  }

  private String summarizeMapping(final Record<?> record) {
    final var value = (MappingRecordValue) record.getValue();

    final StringBuilder builder = new StringBuilder("Mapping[");
    builder
        .append("Key=")
        .append(shortenKey(value.getMappingKey()))
        .append(", claimName=")
        .append(value.getClaimName())
        .append(", claimValue=")
        .append(value.getClaimValue())
        .append("]");

    return builder.toString();
  }

  private String formatPinnedTime(final long time) {
    final var dateTime = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
    return "%s (timestamp: %d)".formatted(shortenDateTime(dateTime), time);
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
}
