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
package io.camunda.zeebe.model.bpmn.validation.zeebe;

import static java.util.stream.Collectors.joining;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import java.util.Arrays;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class TaskListenerValidator implements ModelElementValidator<ZeebeTaskListener> {

  private static final List<ZeebeTaskListenerEventType> SUPPORTED_VALUES =
      Arrays.asList(
          ZeebeTaskListenerEventType.creating,
          ZeebeTaskListenerEventType.assigning,
          ZeebeTaskListenerEventType.updating,
          ZeebeTaskListenerEventType.completing);

  @SuppressWarnings("deprecation")
  private static final List<ZeebeTaskListenerEventType> SUPPORTED_DEPRECATED_VALUES =
      Arrays.asList(
          ZeebeTaskListenerEventType.create,
          ZeebeTaskListenerEventType.assignment,
          ZeebeTaskListenerEventType.update,
          ZeebeTaskListenerEventType.complete);

  @Override
  public Class<ZeebeTaskListener> getElementType() {
    return ZeebeTaskListener.class;
  }

  @Override
  public void validate(
      final ZeebeTaskListener zeebeTaskListener,
      final ValidationResultCollector validationResultCollector) {
    final ZeebeTaskListenerEventType eventType = zeebeTaskListener.getEventType();
    if (eventType != null
        && !SUPPORTED_VALUES.contains(eventType)
        && !SUPPORTED_DEPRECATED_VALUES.contains(eventType)) {
      final String errorMessage =
          String.format(
              "Task listener event type '%s' is not supported. Currently, only %s event types and %s deprecated event types are supported.",
              eventType,
              SUPPORTED_VALUES.stream().map(Enum::name).collect(joining("', '", "'", "'")),
              SUPPORTED_DEPRECATED_VALUES.stream()
                  .map(Enum::name)
                  .collect(joining("', '", "'", "'")));
      validationResultCollector.addError(0, errorMessage);
    }
  }
}
