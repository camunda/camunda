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

package io.zeebe.model.bpmn.builder;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import io.zeebe.model.bpmn.instance.dc.Bounds;

public class AbstractEventSubProcessBuilder<B extends AbstractEventSubProcessBuilder<B>>
    extends AbstractFlowElementBuilder<B, SubProcess> {

  protected AbstractEventSubProcessBuilder(
      BpmnModelInstance modelInstance, SubProcess element, Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  public StartEventBuilder startEvent() {
    return startEvent(null);
  }

  public StartEventBuilder startEvent(String id) {
    final StartEvent start = createChild(StartEvent.class, id);

    final BpmnShape startShape = createBpmnShape(start);
    final BpmnShape subProcessShape = findBpmnShape(getElement());

    if (subProcessShape != null) {
      final Bounds subProcessBounds = subProcessShape.getBounds();
      final Bounds startBounds = startShape.getBounds();

      final double subProcessX = subProcessBounds.getX();
      final double subProcessY = subProcessBounds.getY();
      final double subProcessHeight = subProcessBounds.getHeight();
      final double startHeight = startBounds.getHeight();

      startBounds.setX(subProcessX + SPACE);
      startBounds.setY(subProcessY + subProcessHeight / 2 - startHeight / 2);
    }

    return start.builder();
  }
}
