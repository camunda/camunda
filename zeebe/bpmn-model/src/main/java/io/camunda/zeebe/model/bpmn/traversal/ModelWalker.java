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
package io.camunda.zeebe.model.bpmn.traversal;

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.impl.BpmnModelInstanceImpl;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.camunda.zeebe.model.bpmn.instance.Definitions;
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import org.camunda.bpm.model.xml.impl.util.ModelUtil;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Walks the elements of a {@link BpmnModelInstance} and invokes the provided {@link
 * ModelElementVisitor} for every element.
 *
 * <p>The following order is guaranteed (anything that is not listed here is not guaranteed):
 *
 * <ul>
 *   <li>An element is visited only after its parent has been visited (top-down)
 *   <li>An element's child is visisted before any not yet visited sibling (depth-first)
 * </ul>
 *
 * <p>We can add more constraints to this as we see fit (e.g. certain BPMN elements in the same
 * scope can be visited in a defined order to make transformation more convenient)
 *
 * <p>Depth-first is nice for transformation so we can have some kind of stack with transformation
 * state.
 */
public class ModelWalker {

  private static final Logger LOG = LoggerFactory.getLogger(ModelWalker.class);

  private final BpmnModelInstanceImpl modelInstance;
  private final Deque<BpmnModelElementInstance> elementsToVisit = new LinkedList<>();

  public ModelWalker(final BpmnModelInstance modelInstance) {
    this.modelInstance = (BpmnModelInstanceImpl) modelInstance;
  }

  public void walk(final ModelElementVisitor visitor) {
    final Definitions rootElement = modelInstance.getDefinitions();

    elementsToVisit.add(rootElement); // top-down

    BpmnModelElementInstance currentElement;
    while ((currentElement = elementsToVisit.poll()) != null) {

      // add a new check here for ignore non-executable processes
      if (isNonExecutableProcess(currentElement)) {
        continue;
      }

      visitor.visit(currentElement);
      final Collection<ModelElementInstance> children = getChildElements(currentElement);
      children.forEach(
          c -> {
            if (c instanceof BpmnModelElementInstance) {
              elementsToVisit.addFirst((BpmnModelElementInstance) c);
            } else {
              final ModelElementType elementType = c.getElementType();
              LOG.debug(
                  "Ignoring unknown BPMN element '{}:{}'",
                  elementType.getTypeNamespace(),
                  elementType.getTypeName());
            }
          }); // depth-first
    }
  }

  private Collection<ModelElementInstance> getChildElements(
      final BpmnModelElementInstance element) {
    return ModelUtil.getModelElementCollection(
        element.getDomElement().getChildElements(), modelInstance);
  }

  private boolean isNonExecutableProcess(final BpmnModelElementInstance element) {
    if (element instanceof Process) {
      final Process process = (Process) element;
      return !process.isExecutable();
    } else {
      return false;
    }
  }
}
