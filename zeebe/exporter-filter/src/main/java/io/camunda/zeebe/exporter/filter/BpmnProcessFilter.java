/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Filters records by BPMN process id using simple inclusion/exclusion lists.
 *
 * <p>These are the values allowed by the shouldIndexValueType configuration
 *
 * <p>Supported record values (all of which expose a BPMN process id) are:
 *
 * <ul>
 *   <li>{@link ConditionalSubscriptionRecordValue}
 *   <li>{@link DecisionEvaluationRecordValue}
 *   <li>{@link IncidentRecordValue}
 *   <li>{@link JobRecordValue}
 *   <li>{@link MessageStartEventSubscriptionRecordValue}
 *   <li>{@link MessageSubscriptionRecordValue}
 *   <li>{@link ProcessInstanceCreationRecordValue}
 *   <li>{@link ProcessInstanceRecordValue}
 *   <li>{@link ProcessInstanceResultRecordValue}
 *   <li>{@link ProcessMessageSubscriptionRecordValue}
 *   <li>{@link ProcessMetadataValue}
 *   <li>{@link SignalSubscriptionRecordValue}
 *   <li>{@link UserTaskRecordValue}
 *   <li>{@link VariableRecordValue}
 * </ul>
 *
 * <p>Non-supported record values are always accepted (filter is a no-op for them).
 */
public final class BpmnProcessFilter implements ExporterRecordFilter, RecordVersionFilter {

  private static final SemanticVersion MIN_BROKER_VERSION =
      new SemanticVersion(8, 9, 0, null, null);

  private final Set<String> allowedBpmnProcesses;
  private final Set<String> excludedBpmnProcesses;

  public BpmnProcessFilter(final List<String> inclusion, final List<String> exclusion) {
    final Set<String> inc = normalize(inclusion);
    final Set<String> exc = normalize(exclusion);

    final Set<String> allowed = new HashSet<>(inc);
    allowed.removeAll(exc);

    allowedBpmnProcesses = Collections.unmodifiableSet(allowed);
    excludedBpmnProcesses = Collections.unmodifiableSet(exc);
  }

  private static Set<String> normalize(final List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return Collections.emptySet();
    }
    return Set.copyOf(raw);
  }

  @Override
  public boolean accept(final Record<?> record) {
    final String bpmnProcessId = bpmnProcessIdOf(record.getValue());

    if (bpmnProcessId == null || bpmnProcessId.isEmpty()) {
      return true;
    }

    if (allowedBpmnProcesses.isEmpty()) {
      return !excludedBpmnProcesses.contains(bpmnProcessId);
    }

    return allowedBpmnProcesses.contains(bpmnProcessId);
  }

  @Override
  public SemanticVersion minRecordBrokerVersion() {
    return MIN_BROKER_VERSION;
  }

  private static String bpmnProcessIdOf(final RecordValue value) {
    return switch (value) {
      case final ConditionalSubscriptionRecordValue v -> v.getBpmnProcessId();
      case final DecisionEvaluationRecordValue v -> v.getBpmnProcessId();
      case final IncidentRecordValue v -> v.getBpmnProcessId();
      case final JobRecordValue v -> v.getBpmnProcessId();
      case final MessageStartEventSubscriptionRecordValue v -> v.getBpmnProcessId();
      case final MessageSubscriptionRecordValue v -> v.getBpmnProcessId();
      case final ProcessInstanceCreationRecordValue v -> v.getBpmnProcessId();
      case final ProcessInstanceRecordValue v -> v.getBpmnProcessId();
      case final ProcessInstanceResultRecordValue v -> v.getBpmnProcessId();
      case final ProcessMessageSubscriptionRecordValue v -> v.getBpmnProcessId();
      case final ProcessMetadataValue v -> v.getBpmnProcessId();
      case final SignalSubscriptionRecordValue v -> v.getBpmnProcessId();
      case final UserTaskRecordValue v -> v.getBpmnProcessId();
      case final VariableRecordValue v -> v.getBpmnProcessId();
      default -> null;
    };
  }
}
