/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.protocol.record.intent;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public interface Intent {

  Map<ValueType, Class<? extends Intent>> VALUE_TYPE_TO_INTENT_MAP = computeValueTypeToIntentMap();
  Collection<Class<? extends Intent>> INTENT_CLASSES =
      VALUE_TYPE_TO_INTENT_MAP.values().stream().distinct().collect(Collectors.toList());
  short NULL_VAL = 255;
  Intent UNKNOWN = UnknownIntent.UNKNOWN;

  @SuppressWarnings("deprecated")
  static Map<ValueType, Class<? extends Intent>> computeValueTypeToIntentMap() {
    final Map<ValueType, Class<? extends Intent>> map = new LinkedHashMap<>();
    map.put(ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION, AdHocSubProcessInstructionIntent.class);
    map.put(ValueType.ASYNC_REQUEST, AsyncRequestIntent.class);
    map.put(ValueType.AUTHORIZATION, AuthorizationIntent.class);
    map.put(ValueType.BATCH_OPERATION_CHUNK, BatchOperationChunkIntent.class);
    map.put(ValueType.BATCH_OPERATION_CREATION, BatchOperationIntent.class);
    map.put(ValueType.BATCH_OPERATION_EXECUTION, BatchOperationExecutionIntent.class);
    map.put(ValueType.BATCH_OPERATION_INITIALIZATION, BatchOperationIntent.class);
    map.put(ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, BatchOperationIntent.class);
    map.put(ValueType.BATCH_OPERATION_PARTITION_LIFECYCLE, BatchOperationIntent.class);
    map.put(ValueType.CHECKPOINT, CheckpointIntent.class);
    map.put(ValueType.CLOCK, ClockIntent.class);
    map.put(ValueType.CLUSTER_VARIABLE, ClusterVariableIntent.class);
    map.put(ValueType.COMMAND_DISTRIBUTION, CommandDistributionIntent.class);
    map.put(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.class);
    map.put(ValueType.CONDITIONAL_EVALUATION, ConditionalEvaluationIntent.class);
    map.put(ValueType.CONDITIONAL_SUBSCRIPTION, ConditionalSubscriptionIntent.class);
    map.put(ValueType.DECISION, DecisionIntent.class);
    map.put(ValueType.DECISION_EVALUATION, DecisionEvaluationIntent.class);
    map.put(ValueType.DECISION_REQUIREMENTS, DecisionRequirementsIntent.class);
    map.put(ValueType.DEPLOYMENT, DeploymentIntent.class);
    map.put(ValueType.DEPLOYMENT_DISTRIBUTION, DeploymentDistributionIntent.class);
    map.put(ValueType.ERROR, ErrorIntent.class);
    map.put(ValueType.ESCALATION, EscalationIntent.class);
    map.put(ValueType.EXPRESSION, ExpressionIntent.class);
    map.put(ValueType.FORM, FormIntent.class);
    map.put(ValueType.GLOBAL_LISTENER_BATCH, GlobalListenerBatchIntent.class);
    map.put(ValueType.GROUP, GroupIntent.class);
    map.put(ValueType.HISTORY_DELETION, HistoryDeletionIntent.class);
    map.put(ValueType.IDENTITY_SETUP, IdentitySetupIntent.class);
    map.put(ValueType.INCIDENT, IncidentIntent.class);
    map.put(ValueType.JOB, JobIntent.class);
    map.put(ValueType.JOB_BATCH, JobBatchIntent.class);
    map.put(ValueType.JOB_METRICS_BATCH, JobMetricsBatchIntent.class);
    map.put(ValueType.MAPPING_RULE, MappingRuleIntent.class);
    map.put(ValueType.MESSAGE, MessageIntent.class);
    map.put(ValueType.MESSAGE_BATCH, MessageBatchIntent.class);
    map.put(ValueType.MESSAGE_CORRELATION, MessageCorrelationIntent.class);
    map.put(ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, MessageStartEventSubscriptionIntent.class);
    map.put(ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionIntent.class);
    map.put(ValueType.MULTI_INSTANCE, MultiInstanceIntent.class);
    map.put(ValueType.PROCESS, ProcessIntent.class);
    map.put(ValueType.PROCESS_EVENT, ProcessEventIntent.class);
    map.put(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.class);
    map.put(ValueType.PROCESS_INSTANCE_BATCH, ProcessInstanceBatchIntent.class);
    map.put(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.class);
    map.put(ValueType.PROCESS_INSTANCE_MIGRATION, ProcessInstanceMigrationIntent.class);
    map.put(ValueType.PROCESS_INSTANCE_MODIFICATION, ProcessInstanceModificationIntent.class);
    map.put(ValueType.PROCESS_INSTANCE_RESULT, ProcessInstanceResultIntent.class);
    map.put(ValueType.PROCESS_MESSAGE_SUBSCRIPTION, ProcessMessageSubscriptionIntent.class);
    map.put(ValueType.RESOURCE, ResourceIntent.class);
    map.put(ValueType.RESOURCE_DELETION, ResourceDeletionIntent.class);
    map.put(ValueType.ROLE, RoleIntent.class);
    map.put(ValueType.RUNTIME_INSTRUCTION, RuntimeInstructionIntent.class);
    map.put(ValueType.SCALE, ScaleIntent.class);
    map.put(ValueType.SIGNAL, SignalIntent.class);
    map.put(ValueType.SIGNAL_SUBSCRIPTION, SignalSubscriptionIntent.class);
    map.put(ValueType.TENANT, TenantIntent.class);
    map.put(ValueType.TIMER, TimerIntent.class);
    map.put(ValueType.USAGE_METRIC, UsageMetricIntent.class);
    map.put(ValueType.USER, UserIntent.class);
    map.put(ValueType.USER_TASK, UserTaskIntent.class);
    map.put(ValueType.VARIABLE, VariableIntent.class);
    map.put(ValueType.VARIABLE_DOCUMENT, VariableDocumentIntent.class);
    return map;
  }

  short value();

  String name();

  /**
   * @return true if this intent is used as an event, i.e. it's not a command or command rejection.
   */
  boolean isEvent();

  static Class<? extends Intent> fromValueType(final ValueType valueType) {
    final Class<? extends Intent> intentClass = VALUE_TYPE_TO_INTENT_MAP.get(valueType);
    if (intentClass == null) {
      throw new RuntimeException(
          String.format(
              "Expected to map value type %s to intent type, but did not recognize the value type",
              valueType.name()));
    }
    return intentClass;
  }

  static Intent fromProtocolValue(final ValueType valueType, final short intent) {
    if (valueType == ValueType.NULL_VAL || valueType == ValueType.SBE_UNKNOWN) {
      return Intent.UNKNOWN;
    }
    final Class<? extends Intent> intentClass = fromValueType(valueType);
    for (final Intent intentValue : intentClass.getEnumConstants()) {
      if (intentValue.value() == intent) {
        return intentValue;
      }
    }
    return Intent.UNKNOWN;
  }

  @SuppressWarnings("unchecked")
  static <T extends Enum<T>> Intent fromProtocolValue(
      final ValueType valueType, final String intent) {
    if (valueType == ValueType.NULL_VAL || valueType == ValueType.SBE_UNKNOWN) {
      return Intent.UNKNOWN;
    }
    final Class<? extends Intent> intentClass = fromValueType(valueType);
    try {
      return (Intent) Enum.valueOf((Class<T>) intentClass, intent);
    } catch (final IllegalArgumentException e) {
      return Intent.UNKNOWN;
    }
  }

  static int maxCardinality() {
    return INTENT_CLASSES.stream()
        .mapToInt(clazz -> clazz.getEnumConstants().length)
        .max()
        .getAsInt();
  }

  enum UnknownIntent implements Intent {
    UNKNOWN;

    @Override
    public short value() {
      return NULL_VAL;
    }

    @Override
    public boolean isEvent() {
      return false;
    }
  }
}
