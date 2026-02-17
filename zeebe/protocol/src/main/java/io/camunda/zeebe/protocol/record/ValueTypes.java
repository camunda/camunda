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
package io.camunda.zeebe.protocol.record;

import java.util.EnumSet;
import java.util.stream.Stream;

public final class ValueTypes {

  private static final EnumSet<ValueType> USER_COMMANDS =
      EnumSet.of(
          ValueType.DEPLOYMENT,
          ValueType.JOB,
          ValueType.PROCESS_INSTANCE,
          ValueType.MESSAGE,
          ValueType.MESSAGE_BATCH,
          ValueType.JOB_BATCH,
          ValueType.INCIDENT,
          ValueType.VARIABLE_DOCUMENT,
          ValueType.CLUSTER_VARIABLE,
          ValueType.PROCESS_INSTANCE_CREATION,
          ValueType.DECISION_EVALUATION,
          ValueType.PROCESS_INSTANCE_MODIFICATION,
          ValueType.SIGNAL,
          ValueType.COMMAND_DISTRIBUTION,
          ValueType.PROCESS_INSTANCE_BATCH,
          ValueType.RESOURCE_DELETION,
          ValueType.USER_TASK,
          ValueType.PROCESS_INSTANCE_MIGRATION,
          ValueType.AD_HOC_SUB_PROCESS_INSTRUCTION,
          ValueType.COMPENSATION_SUBSCRIPTION,
          ValueType.MESSAGE_CORRELATION,
          ValueType.USER,
          ValueType.CLOCK,
          ValueType.AUTHORIZATION,
          ValueType.ROLE,
          ValueType.TENANT,
          ValueType.SCALE,
          ValueType.GROUP,
          ValueType.MAPPING_RULE,
          ValueType.IDENTITY_SETUP,
          ValueType.RESOURCE,
          ValueType.BATCH_OPERATION_CREATION,
          ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
          ValueType.USAGE_METRIC,
          ValueType.HISTORY_DELETION,
          ValueType.CONDITIONAL_SUBSCRIPTION,
          ValueType.CONDITIONAL_EVALUATION,
          ValueType.EXPRESSION,
          ValueType.GLOBAL_LISTENER);

  private ValueTypes() {}

  public static boolean isUserCommand(final ValueType valueType) {
    return USER_COMMANDS.contains(valueType);
  }

  public static Stream<ValueType> userCommands() {
    return USER_COMMANDS.stream();
  }
}
