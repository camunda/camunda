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
package io.zeebe.model.bpmn.traversal;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.impl.BpmnImpl;
import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import java.util.List;
import org.camunda.bpm.model.xml.type.ModelElementType;

/**
 * Maintains a registry of visitors per element type (e.g. one for FlowNode, one for ServiceTask,
 * etc.). When visiting an element, it calls the #
 *
 * <ul>
 *   <li>A visitor for a super type is visited before a sub type
 */
public abstract class TypeHierarchyVisitor implements ModelElementVisitor {

  @Override
  public void visit(final BpmnModelElementInstance instance) {
    final ModelElementType type = instance.getElementType();
    final List<ModelElementType> typeHierarchy = getTypeHierarchy(type);

    for (final ModelElementType implementedType : typeHierarchy) {
      visit(implementedType, instance);
    }
  }

  protected abstract void visit(
      ModelElementType implementedType, BpmnModelElementInstance instance);

  private List<ModelElementType> getTypeHierarchy(final ModelElementType type) {
    return ((BpmnImpl) Bpmn.INSTANCE).getHierarchy(type);
  }
}
