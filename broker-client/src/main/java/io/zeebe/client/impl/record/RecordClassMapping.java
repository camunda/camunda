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
package io.zeebe.client.impl.record;

import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.*;
import io.zeebe.client.api.record.*;
import io.zeebe.client.impl.command.*;
import io.zeebe.client.impl.event.*;
import io.zeebe.client.impl.subscription.topic.BiEnumMap;
import java.util.HashMap;
import java.util.Map;

public class RecordClassMapping {
  private static final BiEnumMap<RecordType, ValueType, Class<? extends RecordImpl>> RECORD_CLASSES;

  static {
    RECORD_CLASSES = new BiEnumMap<>(RecordType.class, ValueType.class, Class.class);

    RECORD_CLASSES.put(RecordType.COMMAND, ValueType.JOB, JobCommandImpl.class);
    RECORD_CLASSES.put(RecordType.EVENT, ValueType.JOB, JobEventImpl.class);

    RECORD_CLASSES.put(RecordType.COMMAND, ValueType.INCIDENT, IncidentCommandImpl.class);
    RECORD_CLASSES.put(RecordType.EVENT, ValueType.INCIDENT, IncidentEventImpl.class);

    RECORD_CLASSES.put(RecordType.EVENT, ValueType.RAFT, RaftEventImpl.class);

    RECORD_CLASSES.put(RecordType.COMMAND, ValueType.TOPIC, TopicCommandImpl.class);
    RECORD_CLASSES.put(RecordType.EVENT, ValueType.TOPIC, TopicEventImpl.class);

    RECORD_CLASSES.put(
        RecordType.COMMAND, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceCommandImpl.class);
    RECORD_CLASSES.put(
        RecordType.EVENT, ValueType.WORKFLOW_INSTANCE, WorkflowInstanceEventImpl.class);

    RECORD_CLASSES.put(RecordType.COMMAND, ValueType.DEPLOYMENT, DeploymentCommandImpl.class);
    RECORD_CLASSES.put(RecordType.EVENT, ValueType.DEPLOYMENT, DeploymentEventImpl.class);

    RECORD_CLASSES.put(RecordType.COMMAND, ValueType.TOPIC, TopicCommandImpl.class);
    RECORD_CLASSES.put(RecordType.EVENT, ValueType.TOPIC, TopicEventImpl.class);

    for (ValueType valueType : ValueType.values()) {
      final Class<? extends RecordImpl> commandClass =
          RECORD_CLASSES.get(RecordType.COMMAND, valueType);
      RECORD_CLASSES.put(RecordType.COMMAND_REJECTION, valueType, commandClass);
    }
  }

  private static final Map<Class<?>, Class<?>> RECORD_IMPL_CLASS_MAPPING;

  static {
    RECORD_IMPL_CLASS_MAPPING = new HashMap<>();

    RECORD_IMPL_CLASS_MAPPING.put(JobEvent.class, JobEventImpl.class);
    RECORD_IMPL_CLASS_MAPPING.put(JobCommand.class, JobCommandImpl.class);
    RECORD_IMPL_CLASS_MAPPING.put(WorkflowInstanceEvent.class, WorkflowInstanceEventImpl.class);
    RECORD_IMPL_CLASS_MAPPING.put(WorkflowInstanceCommand.class, WorkflowInstanceCommandImpl.class);
    RECORD_IMPL_CLASS_MAPPING.put(IncidentEvent.class, IncidentEventImpl.class);
    RECORD_IMPL_CLASS_MAPPING.put(IncidentCommand.class, IncidentCommandImpl.class);
    RECORD_IMPL_CLASS_MAPPING.put(RaftEvent.class, RaftEventImpl.class);
    RECORD_IMPL_CLASS_MAPPING.put(DeploymentEvent.class, DeploymentEventImpl.class);
    RECORD_IMPL_CLASS_MAPPING.put(DeploymentCommand.class, DeploymentCommandImpl.class);
    RECORD_IMPL_CLASS_MAPPING.put(TopicEvent.class, TopicEventImpl.class);
    RECORD_IMPL_CLASS_MAPPING.put(TopicCommand.class, TopicCommandImpl.class);
  }

  @SuppressWarnings("unchecked")
  public static <T extends RecordImpl> Class<T> getRecordImplClass(
      RecordType recordType, ValueType valueType) {
    return (Class<T>) RECORD_CLASSES.get(recordType, valueType);
  }

  @SuppressWarnings("unchecked")
  public static <T extends Record> Class<T> getRecordImplClass(Class<T> recordClass) {
    return (Class<T>) RECORD_IMPL_CLASS_MAPPING.get(recordClass);
  }

  @SuppressWarnings("unchecked")
  public static <T extends Record> Class<T> getRecordOfImplClass(Class<T> implClass) {
    return RECORD_IMPL_CLASS_MAPPING
        .entrySet()
        .stream()
        .filter(e -> e.getValue().equals(implClass))
        .findFirst()
        .map(e -> (Class<T>) e.getKey())
        .orElse(null);
  }
}
