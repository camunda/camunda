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
package io.zeebe.model.bpmn.validation.zeebe;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.Error;
import io.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.util.ModelUtil;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ActivityValidator implements ModelElementValidator<Activity> {
  @Override
  public Class<Activity> getElementType() {
    return Activity.class;
  }

  @Override
  public void validate(
      final Activity element, final ValidationResultCollector validationResultCollector) {

    verifyNoDuplicatedMessageNames(element, validationResultCollector);
    verifyNoDuplicatedErrorCodes(element, validationResultCollector);
  }

  private void verifyNoDuplicatedMessageNames(
      final Activity element, final ValidationResultCollector validationResultCollector) {

    final Stream<MessageEventDefinition> boundaryEvents =
        ModelUtil.getBoundaryEvents(element, MessageEventDefinition.class);
    final List<String> duplicateMessageNames = ModelUtil.getDuplicateMessageNames(boundaryEvents);

    duplicateMessageNames.forEach(
        name ->
            validationResultCollector.addError(
                0,
                String.format(
                    "Multiple message boundary events with the same name '%s' are not allowed.",
                    name)));
  }

  private void verifyNoDuplicatedErrorCodes(
      final Activity element, final ValidationResultCollector validationResultCollector) {

    final Map<String, Long> presenceByErrorCode =
        ModelUtil.getBoundaryEvents(element, ErrorEventDefinition.class)
            .filter(e -> e.getError() != null)
            .map(ErrorEventDefinition::getError)
            .filter(error -> error.getErrorCode() != null && !error.getErrorCode().isEmpty())
            .collect(groupingBy(Error::getErrorCode, counting()));

    presenceByErrorCode.entrySet().stream()
        .filter(e -> e.getValue() > 1)
        .map(Entry::getKey)
        .forEach(
            errorCode ->
                validationResultCollector.addError(
                    0,
                    String.format(
                        "Multiple error boundary events with the same errorCode '%s' are not allowed.",
                        errorCode)));
  }
}
