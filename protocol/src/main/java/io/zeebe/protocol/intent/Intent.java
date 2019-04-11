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
package io.zeebe.protocol.intent;

import io.zeebe.protocol.clientapi.ValueType;
import java.util.Arrays;
import java.util.Collection;

public interface Intent {
  Collection<Class<? extends Intent>> INTENT_CLASSES =
      Arrays.asList(
          DeploymentIntent.class,
          IncidentIntent.class,
          JobIntent.class,
          WorkflowInstanceIntent.class,
          MessageIntent.class,
          MessageSubscriptionIntent.class,
          WorkflowInstanceSubscriptionIntent.class,
          ExporterIntent.class,
          JobBatchIntent.class,
          TimerIntent.class,
          VariableIntent.class,
          VariableDocumentIntent.class,
          WorkflowInstanceCreationIntent.class,
          ErrorIntent.class);

  Intent UNKNOWN =
      new Intent() {
        @Override
        public short value() {
          return NULL_VAL;
        }

        @Override
        public String name() {
          return "UNKNOWN";
        }
      };

  short NULL_VAL = 255;

  short value();

  String name();

  static Intent fromProtocolValue(final ValueType valueType, final short intent) {
    switch (valueType) {
      case DEPLOYMENT:
        return DeploymentIntent.from(intent);
      case INCIDENT:
        return IncidentIntent.from(intent);
      case NOOP:
        return Intent.UNKNOWN;
      case JOB:
        return JobIntent.from(intent);
      case WORKFLOW_INSTANCE:
        return WorkflowInstanceIntent.from(intent);
      case MESSAGE:
        return MessageIntent.from(intent);
      case MESSAGE_SUBSCRIPTION:
        return MessageSubscriptionIntent.from(intent);
      case MESSAGE_START_EVENT_SUBSCRIPTION:
        return MessageStartEventSubscriptionIntent.from(intent);
      case WORKFLOW_INSTANCE_SUBSCRIPTION:
        return WorkflowInstanceSubscriptionIntent.from(intent);
      case EXPORTER:
        return ExporterIntent.from(intent);
      case JOB_BATCH:
        return JobBatchIntent.from(intent);
      case TIMER:
        return TimerIntent.from(intent);
      case VARIABLE:
        return VariableIntent.from(intent);
      case VARIABLE_DOCUMENT:
        return VariableDocumentIntent.from(intent);
      case WORKFLOW_INSTANCE_CREATION:
        return WorkflowInstanceCreationIntent.from(intent);
      case ERROR:
        return ErrorIntent.from(intent);
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

  static Intent fromProtocolValue(final ValueType valueType, final String intent) {
    switch (valueType) {
      case DEPLOYMENT:
        return DeploymentIntent.valueOf(intent);
      case INCIDENT:
        return IncidentIntent.valueOf(intent);
      case NOOP:
        return Intent.UNKNOWN;
      case JOB:
        return JobIntent.valueOf(intent);
      case WORKFLOW_INSTANCE:
        return WorkflowInstanceIntent.valueOf(intent);
      case MESSAGE:
        return WorkflowInstanceIntent.valueOf(intent);
      case MESSAGE_SUBSCRIPTION:
        return MessageSubscriptionIntent.valueOf(intent);
      case MESSAGE_START_EVENT_SUBSCRIPTION:
        return MessageStartEventSubscriptionIntent.valueOf(intent);
      case WORKFLOW_INSTANCE_SUBSCRIPTION:
        return WorkflowInstanceSubscriptionIntent.valueOf(intent);
      case EXPORTER:
        return ExporterIntent.valueOf(intent);
      case JOB_BATCH:
        return JobBatchIntent.valueOf(intent);
      case TIMER:
        return TimerIntent.valueOf(intent);
      case VARIABLE:
        return VariableIntent.valueOf(intent);
      case VARIABLE_DOCUMENT:
        return VariableDocumentIntent.valueOf(intent);
      case WORKFLOW_INSTANCE_CREATION:
        return WorkflowInstanceCreationIntent.valueOf(intent);
      case ERROR:
        return ErrorIntent.valueOf(intent);
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
}
