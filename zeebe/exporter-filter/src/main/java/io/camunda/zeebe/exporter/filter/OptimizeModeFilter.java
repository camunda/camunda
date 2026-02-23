/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.EnumSet;

/**
 * Filter that selects only the records relevant for Optimize:
 *
 * <ul>
 *   <li>PROCESS: only {@link ProcessIntent#CREATED}
 *   <li>PROCESS_INSTANCE: only specific intents and BPMN element types
 *   <li>INCIDENT: {@link IncidentIntent#CREATED}, {@link IncidentIntent#RESOLVED}
 *   <li>USER_TASK: ASSIGNED, CANCELED, COMPLETED, CREATING
 *   <li>VARIABLE: CREATED, UPDATED
 * </ul>
 *
 * <p>The filter is applied only if the broker version is at least 8.9.0.
 *
 * <p>Note that this filter is applied after the metadata filter in the exporter pipeline, which is
 * implemented by the {@link RecordFilter} interface via its acceptType, acceptValue, and
 * acceptIntent methods.
 */
public final class OptimizeModeFilter implements ExporterRecordFilter, RecordVersionFilter {

  private static final SemanticVersion MIN_BROKER_VERSION =
      new SemanticVersion(8, 9, 0, null, null);

  private static final EnumSet<ProcessInstanceIntent> ALLOWED_PROCESS_INSTANCE_INTENTS =
      EnumSet.of(
          ProcessInstanceIntent.ELEMENT_COMPLETED,
          ProcessInstanceIntent.ELEMENT_TERMINATED,
          ProcessInstanceIntent.ELEMENT_ACTIVATING);

  private static final EnumSet<BpmnElementType> EXCLUDED_PROCESS_INSTANCE_ELEMENT_TYPES =
      EnumSet.of(BpmnElementType.UNSPECIFIED, BpmnElementType.SEQUENCE_FLOW);

  private static final EnumSet<IncidentIntent> ALLOWED_INCIDENT_INTENTS =
      EnumSet.of(IncidentIntent.CREATED, IncidentIntent.RESOLVED);

  private static final EnumSet<UserTaskIntent> ALLOWED_USER_TASK_INTENTS =
      EnumSet.of(
          UserTaskIntent.ASSIGNED,
          UserTaskIntent.CANCELED,
          UserTaskIntent.COMPLETED,
          UserTaskIntent.CREATING);

  private static final EnumSet<VariableIntent> ALLOWED_VARIABLE_INTENTS =
      EnumSet.of(VariableIntent.CREATED, VariableIntent.UPDATED);

  @Override
  public boolean accept(final Record<?> record) {
    return switch (record.getValueType()) {
      case PROCESS -> isAcceptedProcess(record);
      case PROCESS_INSTANCE -> isAcceptedProcessInstance(record);
      case INCIDENT -> isAcceptedIncident(record);
      case USER_TASK -> isAcceptedUserTask(record);
      case VARIABLE -> isAcceptedVariable(record);
      default -> false;
    };
  }

  private boolean isAcceptedProcess(final Record<?> record) {
    return record.getIntent() == ProcessIntent.CREATED;
  }

  private boolean isAcceptedProcessInstance(final Record<?> record) {
    final var intent = (ProcessInstanceIntent) record.getIntent();

    // Only selected lifecycle events are relevant
    if (!ALLOWED_PROCESS_INSTANCE_INTENTS.contains(intent)) {
      return false;
    }

    // Exclude specific BPMN element types
    final var value = (ProcessInstanceRecordValue) record.getValue();
    final var elementType = value.getBpmnElementType();

    return !EXCLUDED_PROCESS_INSTANCE_ELEMENT_TYPES.contains(elementType);
  }

  private boolean isAcceptedIncident(final Record<?> record) {
    final var intent = (IncidentIntent) record.getIntent();
    return ALLOWED_INCIDENT_INTENTS.contains(intent);
  }

  private boolean isAcceptedUserTask(final Record<?> record) {
    final var intent = (UserTaskIntent) record.getIntent();
    return ALLOWED_USER_TASK_INTENTS.contains(intent);
  }

  private boolean isAcceptedVariable(final Record<?> record) {
    final var intent = (VariableIntent) record.getIntent();
    return ALLOWED_VARIABLE_INTENTS.contains(intent);
  }

  @Override
  public SemanticVersion minRecordBrokerVersion() {
    return MIN_BROKER_VERSION;
  }
}
