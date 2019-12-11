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
package io.zeebe.model.bpmn.util;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModelUtil {
  public static Stream<MessageEventDefinition> getActivityMessageBoundaryEvents(
      final Activity activity) {

    return activity.getBoundaryEvents().stream()
        .flatMap(event -> event.getEventDefinitions().stream())
        .filter(definition -> definition instanceof MessageEventDefinition)
        .map(MessageEventDefinition.class::cast);
  }

  public static List<String> getDuplicateMessageNames(
      final Stream<MessageEventDefinition> eventDefinitions) {

    final Stream<Message> messages =
        eventDefinitions
            .map(MessageEventDefinition::getMessage)
            .filter(m -> m.getName() != null && !m.getName().isEmpty());

    return messages.collect(groupingBy(Message::getName, counting())).entrySet().stream()
        .filter(e -> e.getValue() > 1)
        .map(Entry::getKey)
        .collect(Collectors.toList());
  }
}
