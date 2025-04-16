/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.util;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
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
