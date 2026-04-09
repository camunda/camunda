/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;

import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionDataDto;
import io.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionRecordDto;
import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentDataDto;
import io.camunda.optimize.dto.zeebe.incident.ZeebeIncidentRecordDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableDataDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Purely deterministic factory that builds Zeebe {@link BulkOperation} records for every Zeebe
 * record type.
 *
 * <p>This class has one responsibility: translate domain data into correctly structured Zeebe
 * record DTOs and wrap them as ES bulk index operations. It owns no randomness and no business
 * rules about what data to generate — those concerns belong to {@link VariableCatalogue} (variable
 * values) and {@link NodeTimingSimulator} (duration distribution).
 *
 * <p>Each record type owns its own monotonically increasing position counter so that the Optimize
 * import position tracker advances correctly per index.
 */
class ZeebeRecordFactory {

  private static final String BROKER_VERSION = "8.8.0";

  private final int partitionId;
  private final VariableCatalogue catalogue;
  private final BpmnProvider bpmnProvider;

  private long pdPosition;
  private long piPosition;
  private long varPosition;
  private long utPosition;
  private long incPosition;

  ZeebeRecordFactory(
      final int partitionId,
      final long positionOffset,
      final VariableCatalogue catalogue,
      final BpmnProvider bpmnProvider) {
    this.partitionId = partitionId;
    this.catalogue = catalogue;
    this.bpmnProvider = bpmnProvider;
    pdPosition = positionOffset;
    piPosition = positionOffset;
    varPosition = positionOffset;
    utPosition = positionOffset;
    incPosition = positionOffset;
  }

  // ── Process definition ────────────────────────────────────────────────────

  BulkOperation processDefinitionOp(
      final String bpmnProcessId, final long defKey, final long timestamp) {
    final ZeebeProcessDefinitionDataDto data = new ZeebeProcessDefinitionDataDto();
    data.setBpmnProcessId(bpmnProcessId);
    data.setProcessDefinitionKey(defKey);
    data.setVersion(1);
    data.setResourceName(bpmnProcessId + ".bpmn");
    data.setResource(bpmnProvider.bpmnFor(bpmnProcessId));
    data.setTenantId(ZEEBE_DEFAULT_TENANT_ID);

    final ZeebeProcessDefinitionRecordDto record = new ZeebeProcessDefinitionRecordDto();
    record.setKey(defKey);
    record.setTimestamp(timestamp);
    record.setValueType(ValueType.PROCESS);
    record.setIntent(ProcessIntent.CREATED);
    record.setValue(data);
    return wrap(record, ++pdPosition);
  }

  // ── Process instance ──────────────────────────────────────────────────────

  /**
   * Builds a process instance record.
   *
   * <p>See {@link ElementRecord#flowScopeKey()} for the expected values of that field.
   */
  BulkOperation processInstanceOp(
      final InstanceContext ctx, final ElementRecord element, final LifecycleEvent event) {

    final ZeebeProcessInstanceDataDto data = new ZeebeProcessInstanceDataDto();
    data.setProcessInstanceKey(ctx.instanceKey());
    data.setProcessDefinitionKey(ctx.defKey());
    data.setBpmnProcessId(ctx.processId());
    data.setElementId(element.elementId());
    data.setBpmnElementType(element.type());
    data.setFlowScopeKey(element.flowScopeKey());
    data.setVersion(1);
    data.setTenantId(ZEEBE_DEFAULT_TENANT_ID);

    final ZeebeProcessInstanceRecordDto record = new ZeebeProcessInstanceRecordDto();
    record.setKey(element.key());
    record.setTimestamp(event.timestamp());
    record.setValueType(ValueType.PROCESS_INSTANCE);
    record.setIntent(event.intent());
    record.setValue(data);
    return wrap(record, ++piPosition);
  }

  // ── Variables ─────────────────────────────────────────────────────────────

  /**
   * Builds all variable records for one instance by delegating value generation to the catalogue.
   */
  List<BulkOperation> variableOps(final InstanceContext ctx, final long timestamp) {
    return catalogue.generate(ctx.instanceKey()).stream()
        .map(
            entry ->
                variableOp(ctx, new NamedVariable(entry.getKey(), entry.getValue()), timestamp))
        .collect(Collectors.toList());
  }

  /**
   * Builds {@code UPDATED} variable records for one instance. Used to simulate mid-flight variable
   * updates, which cause the Optimize importer to merge new field values into an existing doc.
   */
  List<BulkOperation> variableUpdateOps(final InstanceContext ctx, final long timestamp) {
    return catalogue.generate(ctx.instanceKey()).stream()
        .map(
            entry ->
                variableUpdateOp(
                    ctx, new NamedVariable(entry.getKey(), entry.getValue()), timestamp))
        .collect(Collectors.toList());
  }

  // ── User task ─────────────────────────────────────────────────────────────

  BulkOperation userTaskCreatingOp(final InstanceContext ctx, final UserTaskEvent event) {
    final ZeebeUserTaskDataDto data = baseUserTaskData(ctx, event);
    return wrapUserTask(data, event, UserTaskIntent.CREATING);
  }

  /** {@code assignee} is supplied by the caller so the factory itself has no randomness. */
  BulkOperation userTaskAssignedOp(
      final InstanceContext ctx, final UserTaskEvent event, final String assignee) {
    final ZeebeUserTaskDataDto data = baseUserTaskData(ctx, event);
    data.setAssignee(assignee);
    data.setChangedAttributes(List.of("assignee"));
    return wrapUserTask(data, event, UserTaskIntent.ASSIGNED);
  }

  BulkOperation userTaskEndOp(
      final InstanceContext ctx, final UserTaskEvent event, final UserTaskIntent intent) {
    final ZeebeUserTaskDataDto data = baseUserTaskData(ctx, event);
    if (intent == UserTaskIntent.COMPLETED) {
      data.setAction("complete");
    }
    return wrapUserTask(data, event, intent);
  }

  // ── Incident ──────────────────────────────────────────────────────────────

  BulkOperation incidentOp(
      final InstanceContext ctx, final IncidentEvent event, final IncidentIntent intent) {

    final ZeebeIncidentDataDto data = new ZeebeIncidentDataDto();
    data.setElementId(event.elementId());
    data.setElementInstanceKey(event.elemInstKey());
    data.setProcessInstanceKey(ctx.instanceKey());
    data.setProcessDefinitionKey(ctx.defKey());
    data.setBpmnProcessId(ctx.processId());
    data.setJobKey(event.elemInstKey());
    data.setVariableScopeKey(ctx.instanceKey());
    data.setErrorType(ErrorType.JOB_NO_RETRIES);
    data.setErrorMessage("Job retries exhausted (simulated)");
    data.setTenantId(ZEEBE_DEFAULT_TENANT_ID);

    final ZeebeIncidentRecordDto record = new ZeebeIncidentRecordDto();
    record.setKey(event.incKey());
    record.setTimestamp(event.timestamp());
    record.setValueType(ValueType.INCIDENT);
    record.setIntent(intent);
    record.setValue(data);
    return wrap(record, ++incPosition);
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private BulkOperation variableUpdateOp(
      final InstanceContext ctx, final NamedVariable variable, final long timestamp) {
    final ZeebeVariableDataDto data = new ZeebeVariableDataDto();
    data.setName(variable.name());
    data.setValue(variable.value());
    data.setScopeKey(ctx.instanceKey());
    data.setProcessInstanceKey(ctx.instanceKey());
    data.setProcessDefinitionKey(ctx.defKey());
    data.setBpmnProcessId(ctx.processId());
    data.setTenantId(ZEEBE_DEFAULT_TENANT_ID);

    final ZeebeVariableRecordDto record = new ZeebeVariableRecordDto();
    record.setKey(++varPosition * 1_000L + Math.abs(variable.name().hashCode() % 1_000));
    record.setTimestamp(timestamp);
    record.setValueType(ValueType.VARIABLE);
    record.setIntent(VariableIntent.UPDATED);
    record.setValue(data);
    return wrap(record, varPosition);
  }

  private BulkOperation variableOp(
      final InstanceContext ctx, final NamedVariable variable, final long timestamp) {
    final ZeebeVariableDataDto data = new ZeebeVariableDataDto();
    data.setName(variable.name());
    data.setValue(variable.value());
    data.setScopeKey(ctx.instanceKey());
    data.setProcessInstanceKey(ctx.instanceKey());
    data.setProcessDefinitionKey(ctx.defKey());
    data.setBpmnProcessId(ctx.processId());
    data.setTenantId(ZEEBE_DEFAULT_TENANT_ID);

    final ZeebeVariableRecordDto record = new ZeebeVariableRecordDto();
    record.setKey(++varPosition * 1_000L + Math.abs(variable.name().hashCode() % 1_000));
    record.setTimestamp(timestamp);
    record.setValueType(ValueType.VARIABLE);
    record.setIntent(VariableIntent.CREATED);
    record.setValue(data);
    return wrap(record, varPosition);
  }

  private ZeebeUserTaskDataDto baseUserTaskData(
      final InstanceContext ctx, final UserTaskEvent event) {
    final ZeebeUserTaskDataDto data = new ZeebeUserTaskDataDto();
    data.setUserTaskKey(event.utKey());
    data.setElementId(event.elementId());
    data.setElementInstanceKey(event.elemInstKey());
    data.setBpmnProcessId(ctx.processId());
    data.setProcessDefinitionKey(ctx.defKey());
    data.setProcessDefinitionVersion(1);
    data.setProcessInstanceKey(ctx.instanceKey());
    data.setTenantId(ZEEBE_DEFAULT_TENANT_ID);
    data.setCreationTimestamp(event.timestamp());
    return data;
  }

  private BulkOperation wrapUserTask(
      final ZeebeUserTaskDataDto data, final UserTaskEvent event, final UserTaskIntent intent) {
    final ZeebeUserTaskRecordDto record = new ZeebeUserTaskRecordDto();
    record.setKey(event.utKey());
    record.setTimestamp(event.timestamp());
    record.setValueType(ValueType.USER_TASK);
    record.setIntent(intent);
    record.setValue(data);
    return wrap(record, ++utPosition);
  }

  /**
   * Populates the common envelope fields on any Zeebe record DTO and wraps it as an ES bulk index
   * operation. All concrete DTO types extend {@link ZeebeRecordDto}, so no instanceof dispatch is
   * needed.
   */
  private BulkOperation wrap(final ZeebeRecordDto<?, ?> record, final long position) {
    record.setPosition(position);
    record.setSequence(position);
    record.setPartitionId(partitionId);
    record.setRecordType(RecordType.EVENT);
    record.setBrokerVersion(BROKER_VERSION);
    final String docId = partitionId + "-" + position;
    return BulkOperation.of(b -> b.index(IndexOperation.of(i -> i.id(docId).document(record))));
  }

  // ── Private parameter objects ─────────────────────────────────────────────

  /** Groups a variable name and its serialized value. Used only inside {@link #variableOp}. */
  private record NamedVariable(String name, String value) {}
}
