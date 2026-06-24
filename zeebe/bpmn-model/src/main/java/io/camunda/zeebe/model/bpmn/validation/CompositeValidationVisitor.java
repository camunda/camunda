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

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.camunda.zeebe.model.bpmn.traversal.TypeHierarchyVisitor;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.validation.ValidationResults;

/**
 * A composite implementation of {@link ValidationVisitor} that delegates visiting to multiple other
 * validation visitors and merges their validation results.
 */
public class CompositeValidationVisitor extends TypeHierarchyVisitor {

  private final List<ValidationVisitor> visitors;

  public CompositeValidationVisitor(final ValidationVisitor... visitors) {
    this.visitors = Arrays.asList(visitors);
  }

  @Override
  protected void visit(
      final ModelElementType implementedType, final BpmnModelElementInstance instance) {
    for (final ValidationVisitor visitor : visitors) {
      visitor.visit(implementedType, instance);
    }
  }

  public void reset() {
    for (final ValidationVisitor visitor : visitors) {
      visitor.reset();
    }
  }

  public List<ValidationResults> getValidationResults() {
    return visitors.stream()
        .map(ValidationVisitor::getValidationResult)
        .collect(Collectors.toList());
  }
}
