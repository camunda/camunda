/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord.Builder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.time.Instant;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public abstract class ZeebeRecordTestUtil {

  @NotNull
  public static Record<IncidentRecordValue> createZeebeRecordFromIncident(
      final IncidentEntity inc,
      final Consumer<Builder> recordBuilderFunction,
      final Consumer<ImmutableIncidentRecordValue.Builder> recordValueBuilderFunction) {
    final Builder<IncidentRecordValue> builder = ImmutableRecord.builder();
    final ImmutableIncidentRecordValue.Builder valueBuilder =
        ImmutableIncidentRecordValue.builder();
    valueBuilder
        .withBpmnProcessId(inc.getBpmnProcessId())
        .withElementInstanceKey(inc.getFlowNodeInstanceKey())
        .withErrorMessage(inc.getErrorMessage())
        .withElementId(inc.getFlowNodeId())
        .withBpmnProcessId(inc.getBpmnProcessId())
        .withErrorType(ErrorType.valueOf(inc.getErrorType().name()))
        .withTenantId(inc.getTenantId());
    if (recordValueBuilderFunction != null) {
      recordValueBuilderFunction.accept(valueBuilder);
    }
    builder
        .withKey(inc.getKey())
        .withPartitionId(1)
        .withTimestamp(Instant.now().toEpochMilli())
        .withValue(valueBuilder.build())
        .withValueType(ValueType.INCIDENT);
    if (recordBuilderFunction != null) {
      recordBuilderFunction.accept(builder);
    }
    return builder.build();
  }

  @NotNull
  public static Record<JobRecordValue> createJobZeebeRecord(
      final Consumer<Builder> recordBuilderFunction,
      final Consumer<ImmutableJobRecordValue.Builder> recordValueBuilderFunction) {
    final Builder<JobRecordValue> builder = ImmutableRecord.builder();
    final ImmutableJobRecordValue.Builder valueBuilder = ImmutableJobRecordValue.builder();
    if (recordValueBuilderFunction != null) {
      recordValueBuilderFunction.accept(valueBuilder);
    }
    builder
        .withPartitionId(1)
        .withTimestamp(Instant.now().toEpochMilli())
        .withValue(valueBuilder.build())
        .withValueType(ValueType.JOB);
    if (recordBuilderFunction != null) {
      recordBuilderFunction.accept(builder);
    }
    return builder.build();
  }

  @NotNull
  public static Record<ProcessMessageSubscriptionRecordValue>
      createProcessMessageSubscriptionZeebeRecord(
          final Consumer<Builder> recordBuilderFunction,
          final Consumer<ImmutableProcessMessageSubscriptionRecordValue.Builder>
              recordValueBuilderFunction) {
    final Builder<ProcessMessageSubscriptionRecordValue> builder = ImmutableRecord.builder();
    final ImmutableProcessMessageSubscriptionRecordValue.Builder valueBuilder =
        ImmutableProcessMessageSubscriptionRecordValue.builder();
    if (recordValueBuilderFunction != null) {
      recordValueBuilderFunction.accept(valueBuilder);
    }
    builder
        .withPartitionId(1)
        .withTimestamp(Instant.now().toEpochMilli())
        .withValue(valueBuilder.build())
        .withValueType(ValueType.PROCESS_MESSAGE_SUBSCRIPTION);
    if (recordBuilderFunction != null) {
      recordBuilderFunction.accept(builder);
    }
    return builder.build();
  }

  @NotNull
  public static Record<IncidentRecordValue> createIncidentZeebeRecord(
      final Consumer<Builder> recordBuilderFunction,
      final Consumer<ImmutableIncidentRecordValue.Builder> recordValueBuilderFunction) {
    final Builder<IncidentRecordValue> builder = ImmutableRecord.builder();
    final ImmutableIncidentRecordValue.Builder valueBuilder =
        ImmutableIncidentRecordValue.builder();
    if (recordValueBuilderFunction != null) {
      recordValueBuilderFunction.accept(valueBuilder);
    }
    builder
        .withPartitionId(1)
        .withTimestamp(Instant.now().toEpochMilli())
        .withValue(valueBuilder.build())
        .withValueType(ValueType.PROCESS_INSTANCE);
    if (recordBuilderFunction != null) {
      recordBuilderFunction.accept(builder);
    }
    return builder.build();
  }

  @NotNull
  public static Record<ProcessInstanceRecordValue> createProcessInstanceZeebeRecord(
      final Consumer<Builder> recordBuilderFunction,
      final Consumer<ImmutableProcessInstanceRecordValue.Builder> recordValueBuilderFunction) {
    final Builder<ProcessInstanceRecordValue> builder = ImmutableRecord.builder();
    final ImmutableProcessInstanceRecordValue.Builder valueBuilder =
        ImmutableProcessInstanceRecordValue.builder();
    if (recordValueBuilderFunction != null) {
      recordValueBuilderFunction.accept(valueBuilder);
    }
    builder
        .withPartitionId(1)
        .withTimestamp(Instant.now().toEpochMilli())
        .withValue(valueBuilder.build())
        .withValueType(ValueType.PROCESS_INSTANCE);
    if (recordBuilderFunction != null) {
      recordBuilderFunction.accept(builder);
    }
    return builder.build();
  }

  @NotNull
  public static Record<ProcessInstanceRecordValue> createZeebeRecordFromPi(
      final ProcessInstanceForListViewEntity pi,
      final Consumer<Builder> recordBuilderFunction,
      final Consumer<ImmutableProcessInstanceRecordValue.Builder> recordValueBuilderFunction) {
    final Builder<ProcessInstanceRecordValue> builder = ImmutableRecord.builder();
    final ImmutableProcessInstanceRecordValue.Builder valueBuilder =
        ImmutableProcessInstanceRecordValue.builder();
    valueBuilder
        .withProcessInstanceKey(pi.getProcessInstanceKey())
        .withBpmnProcessId(pi.getBpmnProcessId())
        .withBpmnElementType(BpmnElementType.PROCESS)
        .withProcessDefinitionKey(pi.getProcessDefinitionKey())
        .withVersion(pi.getProcessVersion())
        .withTenantId(pi.getTenantId());
    if (recordValueBuilderFunction != null) {
      recordValueBuilderFunction.accept(valueBuilder);
    }
    builder
        .withKey(pi.getKey())
        .withPartitionId(1)
        .withTimestamp(Instant.now().toEpochMilli())
        .withValue(valueBuilder.build());
    if (recordBuilderFunction != null) {
      recordBuilderFunction.accept(builder);
    }
    return builder.build();
  }

  @NotNull
  public static Record<ProcessInstanceRecordValue> createZeebeRecordFromFni(
      final FlowNodeInstanceForListViewEntity fni,
      final Consumer<Builder> recordBuilderFunction,
      final Consumer<ImmutableProcessInstanceRecordValue.Builder> recordValueBuilderFunction) {
    final Builder<ProcessInstanceRecordValue> builder = ImmutableRecord.builder();
    final ImmutableProcessInstanceRecordValue.Builder valueBuilder =
        ImmutableProcessInstanceRecordValue.builder();
    valueBuilder
        .withBpmnElementType(BpmnElementType.valueOf(fni.getActivityType().toString()))
        .withElementId(fni.getActivityId())
        .withProcessInstanceKey(fni.getProcessInstanceKey())
        .withTenantId(fni.getTenantId());
    if (recordValueBuilderFunction != null) {
      recordValueBuilderFunction.accept(valueBuilder);
    }
    builder
        .withKey(fni.getKey())
        .withPartitionId(1)
        .withTimestamp(Instant.now().toEpochMilli())
        .withValue(valueBuilder.build());
    if (recordBuilderFunction != null) {
      recordBuilderFunction.accept(builder);
    }
    return builder.build();
  }

  @NotNull
  public static Record<VariableRecordValue> createZeebeRecordFromVariable(
      final VariableEntity var,
      final Consumer<Builder> recordBuilderFunction,
      final Consumer<ImmutableVariableRecordValue.Builder> recordValueBuilderFunction) {
    final Builder<VariableRecordValue> builder = ImmutableRecord.builder();
    final ImmutableVariableRecordValue.Builder valueBuilder =
        ImmutableVariableRecordValue.builder();
    valueBuilder
        .withName(var.getName())
        .withValue(var.getFullValue())
        .withProcessInstanceKey(var.getProcessInstanceKey())
        .withProcessDefinitionKey(var.getProcessDefinitionKey())
        .withBpmnProcessId(var.getBpmnProcessId())
        .withScopeKey(var.getScopeKey())
        .withTenantId(var.getTenantId());
    if (recordValueBuilderFunction != null) {
      recordValueBuilderFunction.accept(valueBuilder);
    }
    builder
        .withPartitionId(1)
        .withTimestamp(Instant.now().toEpochMilli())
        .withValue(valueBuilder.build());
    if (recordBuilderFunction != null) {
      recordBuilderFunction.accept(builder);
    }
    return builder.build();
  }
}
