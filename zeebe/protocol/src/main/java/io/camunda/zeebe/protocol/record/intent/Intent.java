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
import java.util.Arrays;
import java.util.Collection;

public interface Intent {
  Collection<Class<? extends Intent>> INTENT_CLASSES =
      Arrays.asList(
          DeploymentIntent.class,
          EscalationIntent.class,
          IncidentIntent.class,
          JobIntent.class,
          ProcessInstanceIntent.class,
          MessageIntent.class,
          MessageBatchIntent.class,
          MessageSubscriptionIntent.class,
          ProcessMessageSubscriptionIntent.class,
          JobBatchIntent.class,
          TimerIntent.class,
          VariableIntent.class,
          VariableDocumentIntent.class,
          ProcessInstanceCreationIntent.class,
          ErrorIntent.class,
          ProcessIntent.class,
          DeploymentDistributionIntent.class,
          ProcessEventIntent.class,
          DecisionIntent.class,
          DecisionRequirementsIntent.class,
          DecisionEvaluationIntent.class,
          MessageStartEventSubscriptionIntent.class,
          ProcessInstanceResultIntent.class,
          CheckpointIntent.class,
          ProcessInstanceModificationIntent.class,
          SignalIntent.class,
          SignalSubscriptionIntent.class,
          ResourceDeletionIntent.class,
          CommandDistributionIntent.class,
          ProcessInstanceBatchIntent.class,
          FormIntent.class,
          ResourceIntent.class,
          UserTaskIntent.class,
          ProcessInstanceMigrationIntent.class,
          CompensationSubscriptionIntent.class,
          MessageCorrelationIntent.class,
          UserIntent.class,
          ClockIntent.class,
          AuthorizationIntent.class,
          RoleIntent.class,
          TenantIntent.class,
          ScaleIntent.class,
          GroupIntent.class,
          MappingRuleIntent.class,
          IdentitySetupIntent.class,
          BatchOperationIntent.class,
          BatchOperationChunkIntent.class,
          BatchOperationExecutionIntent.class,
          AdHocSubProcessInstructionIntent.class,
          AsyncRequestIntent.class,
          UsageMetricIntent.class,
          MultiInstanceIntent.class,
          RuntimeInstructionIntent.class);
  short NULL_VAL = 255;
  Intent UNKNOWN = UnknownIntent.UNKNOWN;

  short value();

  String name();

  /**
   * @return true if this intent is used as an event, i.e. it's not a command or command rejection.
   */
  boolean isEvent();

  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  static Intent fromProtocolValue(final ValueType valueType, final short intent) {
    switch (valueType) {
      case DEPLOYMENT:
        return DeploymentIntent.from(intent);
      case INCIDENT:
        return IncidentIntent.from(intent);
      case JOB:
        return JobIntent.from(intent);
      case PROCESS_INSTANCE:
        return ProcessInstanceIntent.from(intent);
      case MESSAGE:
        return MessageIntent.from(intent);
      case MESSAGE_BATCH:
        return MessageBatchIntent.from(intent);
      case MESSAGE_SUBSCRIPTION:
        return MessageSubscriptionIntent.from(intent);
      case MESSAGE_START_EVENT_SUBSCRIPTION:
        return MessageStartEventSubscriptionIntent.from(intent);
      case PROCESS_MESSAGE_SUBSCRIPTION:
        return ProcessMessageSubscriptionIntent.from(intent);
      case JOB_BATCH:
        return JobBatchIntent.from(intent);
      case TIMER:
        return TimerIntent.from(intent);
      case VARIABLE:
        return VariableIntent.from(intent);
      case VARIABLE_DOCUMENT:
        return VariableDocumentIntent.from(intent);
      case PROCESS_INSTANCE_CREATION:
        return ProcessInstanceCreationIntent.from(intent);
      case ERROR:
        return ErrorIntent.from(intent);
      case PROCESS_INSTANCE_RESULT:
        return ProcessInstanceResultIntent.from(intent);
      case PROCESS:
        return ProcessIntent.from(intent);
      case DEPLOYMENT_DISTRIBUTION:
        return DeploymentDistributionIntent.from(intent);
      case PROCESS_EVENT:
        return ProcessEventIntent.from(intent);
      case DECISION:
        return DecisionIntent.from(intent);
      case DECISION_REQUIREMENTS:
        return DecisionRequirementsIntent.from(intent);
      case DECISION_EVALUATION:
        return DecisionEvaluationIntent.from(intent);
      case CHECKPOINT:
        return CheckpointIntent.from(intent);
      case ESCALATION:
        return EscalationIntent.from(intent);
      case PROCESS_INSTANCE_MODIFICATION:
        return ProcessInstanceModificationIntent.from(intent);
      case SIGNAL:
        return SignalIntent.from(intent);
      case SIGNAL_SUBSCRIPTION:
        return SignalSubscriptionIntent.from(intent);
      case RESOURCE_DELETION:
        return ResourceDeletionIntent.from(intent);
      case COMMAND_DISTRIBUTION:
        return CommandDistributionIntent.from(intent);
      case PROCESS_INSTANCE_BATCH:
        return ProcessInstanceBatchIntent.from(intent);
      case AD_HOC_SUB_PROCESS_INSTRUCTION:
        return AdHocSubProcessInstructionIntent.from(intent);
      case FORM:
        return FormIntent.from(intent);
      case RESOURCE:
        return ResourceIntent.from(intent);
      case USER_TASK:
        return UserTaskIntent.from(intent);
      case PROCESS_INSTANCE_MIGRATION:
        return ProcessInstanceMigrationIntent.from(intent);
      case COMPENSATION_SUBSCRIPTION:
        return CompensationSubscriptionIntent.from(intent);
      case MESSAGE_CORRELATION:
        return MessageCorrelationIntent.from(intent);
      case USER:
        return UserIntent.from(intent);
      case CLOCK:
        return ClockIntent.from(intent);
      case AUTHORIZATION:
        return AuthorizationIntent.from(intent);
      case ROLE:
        return RoleIntent.from(intent);
      case TENANT:
        return TenantIntent.from(intent);
      case SCALE:
        return ScaleIntent.from(intent);
      case GROUP:
        return GroupIntent.from(intent);
      case MAPPING_RULE:
        return MappingRuleIntent.from(intent);
      case IDENTITY_SETUP:
        return IdentitySetupIntent.from(intent);
      case BATCH_OPERATION_CREATION:
        return BatchOperationIntent.from(intent);
      case BATCH_OPERATION_EXECUTION:
        return BatchOperationExecutionIntent.from(intent);
      case BATCH_OPERATION_CHUNK:
        return BatchOperationChunkIntent.from(intent);
      case BATCH_OPERATION_LIFECYCLE_MANAGEMENT:
        return BatchOperationIntent.from(intent);
      case BATCH_OPERATION_PARTITION_LIFECYCLE:
        return BatchOperationIntent.from(intent);
      case BATCH_OPERATION_INITIALIZATION:
        return BatchOperationIntent.from(intent);
      case ASYNC_REQUEST:
        return AsyncRequestIntent.from(intent);
      case USAGE_METRIC:
        return UsageMetricIntent.from(intent);
      case MULTI_INSTANCE:
        return MultiInstanceIntent.from(intent);
      case RUNTIME_INSTRUCTION:
        return RuntimeInstructionIntent.from(intent);
      case NULL_VAL:
      case SBE_UNKNOWN:
        return Intent.UNKNOWN;
    }

    throw new RuntimeException(
        String.format(
            "Expected to map value type %s to intent type, but did not recognize the value type",
            valueType.name()));
  }

  static Intent fromProtocolValue(final ValueType valueType, final String intent) {
    switch (valueType) {
      case DEPLOYMENT:
        return DeploymentIntent.valueOf(intent);
      case INCIDENT:
        return IncidentIntent.valueOf(intent);
      case JOB:
        return JobIntent.valueOf(intent);
      case PROCESS_INSTANCE:
        return ProcessInstanceIntent.valueOf(intent);
      case MESSAGE:
        return MessageIntent.valueOf(intent);
      case MESSAGE_BATCH:
        return MessageBatchIntent.valueOf(intent);
      case MESSAGE_SUBSCRIPTION:
        return MessageSubscriptionIntent.valueOf(intent);
      case MESSAGE_START_EVENT_SUBSCRIPTION:
        return MessageStartEventSubscriptionIntent.valueOf(intent);
      case PROCESS_MESSAGE_SUBSCRIPTION:
        return ProcessMessageSubscriptionIntent.valueOf(intent);
      case JOB_BATCH:
        return JobBatchIntent.valueOf(intent);
      case TIMER:
        return TimerIntent.valueOf(intent);
      case VARIABLE:
        return VariableIntent.valueOf(intent);
      case VARIABLE_DOCUMENT:
        return VariableDocumentIntent.valueOf(intent);
      case PROCESS_INSTANCE_CREATION:
        return ProcessInstanceCreationIntent.valueOf(intent);
      case ERROR:
        return ErrorIntent.valueOf(intent);
      case PROCESS_INSTANCE_RESULT:
        return ProcessInstanceResultIntent.valueOf(intent);
      case PROCESS:
        return ProcessIntent.valueOf(intent);
      case DEPLOYMENT_DISTRIBUTION:
        return DeploymentDistributionIntent.valueOf(intent);
      case PROCESS_EVENT:
        return ProcessEventIntent.valueOf(intent);
      case AD_HOC_SUB_PROCESS_INSTRUCTION:
        return AdHocSubProcessInstructionIntent.valueOf(intent);
      case DECISION:
        return DecisionIntent.valueOf(intent);
      case DECISION_REQUIREMENTS:
        return DecisionRequirementsIntent.valueOf(intent);
      case DECISION_EVALUATION:
        return DecisionEvaluationIntent.valueOf(intent);
      case CHECKPOINT:
        return CheckpointIntent.valueOf(intent);
      case ESCALATION:
        return EscalationIntent.valueOf(intent);
      case SIGNAL:
        return SignalIntent.valueOf(intent);
      case SIGNAL_SUBSCRIPTION:
        return SignalSubscriptionIntent.valueOf(intent);
      case RESOURCE_DELETION:
        return ResourceDeletionIntent.valueOf(intent);
      case FORM:
        return FormIntent.valueOf(intent);
      case RESOURCE:
        return ResourceIntent.valueOf(intent);
      case USER_TASK:
        return UserTaskIntent.valueOf(intent);
      case PROCESS_INSTANCE_MIGRATION:
        return ProcessInstanceMigrationIntent.valueOf(intent);
      case COMPENSATION_SUBSCRIPTION:
        return CompensationSubscriptionIntent.valueOf(intent);
      case MESSAGE_CORRELATION:
        return MessageCorrelationIntent.valueOf(intent);
      case USER:
        return UserIntent.valueOf(intent);
      case CLOCK:
        return ClockIntent.valueOf(intent);
      case AUTHORIZATION:
        return AuthorizationIntent.valueOf(intent);
      case ROLE:
        return RoleIntent.valueOf(intent);
      case TENANT:
        return TenantIntent.valueOf(intent);
      case SCALE:
        return ScaleIntent.valueOf(intent);
      case GROUP:
        return GroupIntent.valueOf(intent);
      case MAPPING_RULE:
        return MappingRuleIntent.valueOf(intent);
      case IDENTITY_SETUP:
        return IdentitySetupIntent.valueOf(intent);
      case BATCH_OPERATION_CREATION:
        return BatchOperationIntent.valueOf(intent);
      case BATCH_OPERATION_EXECUTION:
        return BatchOperationExecutionIntent.valueOf(intent);
      case BATCH_OPERATION_CHUNK:
        return BatchOperationChunkIntent.valueOf(intent);
      case BATCH_OPERATION_LIFECYCLE_MANAGEMENT:
        return BatchOperationIntent.valueOf(intent);
      case BATCH_OPERATION_PARTITION_LIFECYCLE:
        return BatchOperationIntent.valueOf(intent);
      case BATCH_OPERATION_INITIALIZATION:
        return BatchOperationIntent.valueOf(intent);
      case ASYNC_REQUEST:
        return AsyncRequestIntent.valueOf(intent);
      case RUNTIME_INSTRUCTION:
        return RuntimeInstructionIntent.valueOf(intent);
      case NULL_VAL:
      case SBE_UNKNOWN:
        return Intent.UNKNOWN;
      default:
        throw new RuntimeException(
            String.format(
                "Expected to map value type %s to intent type, but did not recognize the value type",
                valueType.name()));
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
