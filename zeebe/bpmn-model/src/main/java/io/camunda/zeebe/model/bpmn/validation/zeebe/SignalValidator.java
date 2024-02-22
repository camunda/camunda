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

import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.EventDefinition;
import io.camunda.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.Signal;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.camunda.bpm.model.xml.impl.ModelInstanceImpl;
import org.camunda.bpm.model.xml.impl.util.ModelUtil;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class SignalValidator implements ModelElementValidator<Signal> {

  @Override
  public Class<Signal> getElementType() {
    return Signal.class;
  }

  @Override
  public void validate(
      final Signal element, final ValidationResultCollector validationResultCollector) {

    if (isReferredByCatchEvent(element)
        || isReferredByThrowEvent(element)
        || isReferredByEndEvent(element)
        || isReferredByEventSubProcessStartEvent(element)) {
      validateName(element, validationResultCollector);
    }

    validateIfReferredByStartEvent(element, validationResultCollector);
  }

  private void validateIfReferredByStartEvent(
      final Signal element, final ValidationResultCollector validationResultCollector) {

    final Collection<StartEvent> startEvents =
        element.getParentElement().getChildElementsByType(Process.class).stream()
            .flatMap(p -> p.getChildElementsByType(StartEvent.class).stream())
            .collect(Collectors.toList());

    final List<EventDefinition> referredStartEvents =
        startEvents.stream()
            .flatMap(i -> i.getEventDefinitions().stream())
            .filter(
                e ->
                    e instanceof SignalEventDefinition
                        && ((SignalEventDefinition) e).getSignal() == element)
            .collect(Collectors.toList());

    if (referredStartEvents.size() > 1) {
      validateList(referredStartEvents, validationResultCollector);
    } else if (referredStartEvents.size() == 1) {
      validateName(element, validationResultCollector);
    }
  }

  private boolean isReferredByCatchEvent(final Signal element) {
    final Collection<IntermediateCatchEvent> intermediateCatchEvents =
        getAllElementsByType(element, IntermediateCatchEvent.class);

    final Collection<BoundaryEvent> boundaryEvents =
        getAllElementsByType(element, BoundaryEvent.class);

    return Stream.concat(intermediateCatchEvents.stream(), boundaryEvents.stream())
        .flatMap(i -> i.getEventDefinitions().stream())
        .anyMatch(
            e ->
                e instanceof SignalEventDefinition
                    && ((SignalEventDefinition) e).getSignal() == element);
  }

  private boolean isReferredByThrowEvent(final Signal element) {
    final Collection<IntermediateThrowEvent> intermediateThrowEvents =
        getAllElementsByType(element, IntermediateThrowEvent.class);

    return intermediateThrowEvents.stream()
        .flatMap(i -> i.getEventDefinitions().stream())
        .anyMatch(
            e ->
                e instanceof SignalEventDefinition
                    && ((SignalEventDefinition) e).getSignal() == element);
  }

  private boolean isReferredByEndEvent(final Signal element) {
    final Collection<EndEvent> endEvents = getAllElementsByType(element, EndEvent.class);

    return endEvents.stream()
        .flatMap(i -> i.getEventDefinitions().stream())
        .anyMatch(
            e ->
                e instanceof SignalEventDefinition
                    && ((SignalEventDefinition) e).getSignal() == element);
  }

  private boolean isReferredByEventSubProcessStartEvent(final Signal element) {
    final Collection<StartEvent> startEvents =
        element.getParentElement().getChildElementsByType(Process.class).stream()
            .flatMap(p -> p.getChildElementsByType(SubProcess.class).stream())
            .flatMap(p -> p.getChildElementsByType(StartEvent.class).stream())
            .collect(Collectors.toList());
    final long numReferredSubProcessStartEvents =
        startEvents.stream()
            .flatMap(i -> i.getEventDefinitions().stream())
            .filter(
                e ->
                    e instanceof SignalEventDefinition
                        && ((SignalEventDefinition) e).getSignal() == element)
            .count();
    return numReferredSubProcessStartEvents == 1;
  }

  private void validateName(
      final Signal element, final ValidationResultCollector validationResultCollector) {
    if (element.getName() == null || element.getName().isEmpty()) {
      validationResultCollector.addError(0, "Name must be present and not empty");
    }
  }

  private void validateList(
      final List<EventDefinition> catchEvents,
      final ValidationResultCollector validationResultCollector) {

    catchEvents.forEach(
        event -> {
          final SignalEventDefinition e = (SignalEventDefinition) event;
          validateName(e.getSignal(), validationResultCollector);
        });
  }

  private <T extends ModelElementInstance> Collection<T> getAllElementsByType(
      final Signal element, final Class<T> type) {
    return element.getParentElement().getChildElementsByType(Process.class).stream()
        .flatMap(p -> getAllElementsByTypeRecursive(p, type).stream())
        .collect(Collectors.toList());
  }

  private <T extends ModelElementInstance> Collection<T> getAllElementsByTypeRecursive(
      final ModelElementInstance element, final Class<T> type) {

    // look for immediate children
    final Collection<T> result = element.getChildElementsByType(type);

    // look for children in subtree
    final List<DomElement> childDomElements = element.getDomElement().getChildElements();
    final Collection<ModelElementInstance> childModelElements =
        ModelUtil.getModelElementCollection(
            childDomElements, (ModelInstanceImpl) element.getModelInstance());

    result.addAll(
        childModelElements.stream()
            .flatMap(child -> getAllElementsByTypeRecursive(child, type).stream())
            .collect(Collectors.toList()));

    return result;
  }
}
