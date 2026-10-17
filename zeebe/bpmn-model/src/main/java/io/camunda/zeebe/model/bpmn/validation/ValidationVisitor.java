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
package io.camunda.zeebe.model.bpmn.validation;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.camunda.zeebe.model.bpmn.traversal.TypeHierarchyVisitor;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.camunda.bpm.model.xml.impl.validation.ValidationResultsCollectorImpl;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResults;

public class ValidationVisitor extends TypeHierarchyVisitor {

  private final Map<Class, List<ModelElementValidator>> validators;
  private final Supplier<Collection<ModelElementValidator<?>>> statefulValidatorsSupplier;
  private Map<Class, List<ModelElementValidator>> statefulValidators;

  private ValidationResultsCollectorImpl resultCollector;

  public ValidationVisitor(final Collection<ModelElementValidator<?>> validators) {
    this(groupByType(validators), Collections::emptyList);
  }

  /**
   * @param validators stateless validators, already grouped by {@link #groupByType}, which can be
   *     shared between visitors
   * @param statefulValidatorsSupplier creates validators that keep state during a walk; it is
   *     called again on {@link #reset()}, so that no state leaks into the next walk. For an element
   *     they run before the stateless ones, which keeps the order of the reported errors as it was
   *     when they were registered first.
   */
  public ValidationVisitor(
      final Map<Class, List<ModelElementValidator>> validators,
      final Supplier<Collection<ModelElementValidator<?>>> statefulValidatorsSupplier) {
    this.validators = validators;
    this.statefulValidatorsSupplier = statefulValidatorsSupplier;
    statefulValidators = groupByType(statefulValidatorsSupplier.get());
    resultCollector = new ValidationResultsCollectorImpl();
  }

  public static Map<Class, List<ModelElementValidator>> groupByType(
      final Collection<ModelElementValidator<?>> validators) {
    return Collections.unmodifiableMap(
        validators.stream()
            .collect(
                groupingBy(
                    ModelElementValidator::getElementType,
                    collectingAndThen(toList(), Collections::unmodifiableList))));
  }

  @Override
  protected void visit(
      final ModelElementType implementedType, final BpmnModelElementInstance instance) {

    resultCollector.setCurrentElement(instance);

    final Class<?> type = implementedType.getInstanceType();
    statefulValidators
        .getOrDefault(type, Collections.emptyList())
        .forEach(validator -> validator.validate(instance, resultCollector));
    validators
        .getOrDefault(type, Collections.emptyList())
        .forEach(validator -> validator.validate(instance, resultCollector));
  }

  public void reset() {
    resultCollector = new ValidationResultsCollectorImpl();
    statefulValidators = groupByType(statefulValidatorsSupplier.get());
  }

  public ValidationResults getValidationResult() {
    return resultCollector.getResults();
  }
}
