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

package io.camunda.zeebe.model.bpmn.builder;

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import io.camunda.zeebe.model.bpmn.instance.dc.Bounds;
import java.util.function.Consumer;

/**
 * @author Sebastian Menski
 */
public class SubProcessBuilder extends AbstractSubProcessBuilder<SubProcessBuilder> {

  public SubProcessBuilder(final BpmnModelInstance modelInstance, final SubProcess element) {
    super(modelInstance, element, SubProcessBuilder.class);
  }

  public EventSubProcessBuilder eventSubProcess() {
    return eventSubProcess(null);
  }

  public EventSubProcessBuilder eventSubProcess(final String id) {
    // Create a subprocess, triggered by an event, and add it to modelInstance
    final SubProcess subProcess = createChild(SubProcess.class, id);
    subProcess.setTriggeredByEvent(true);

    // Create Bpmn shape so subprocess will be drawn
    final BpmnShape targetBpmnShape = createBpmnShape(subProcess);
    // find the lowest shape in the process
    // place event sub process underneath
    setEventCoordinates(targetBpmnShape);

    resizeBpmnShape(targetBpmnShape);

    return new EventSubProcessBuilder(modelInstance, subProcess);
  }

  public SubProcessBuilder eventSubProcess(
      final String id, final Consumer<EventSubProcessBuilder> consumer) {
    final EventSubProcessBuilder builder = eventSubProcess(id);
    consumer.accept(builder);
    return this;
  }

  protected void setEventCoordinates(final BpmnShape targetBpmnShape) {
    final BpmnShape subProcessShape = findBpmnShape(getElement());
    if (subProcessShape != null) {
      final Bounds subProcessBounds = subProcessShape.getBounds();
      final Bounds targetBounds = targetBpmnShape.getBounds();
      final double subProcessX = subProcessBounds.getX();
      targetBounds.setX(subProcessX + SPACE);
      targetBounds.setY(getLowestHeight() + SPACE);
    }
  }

  private double getLowestHeight() {
    double lowestHeight = 0;

    for (final FlowElement element : element.getFlowElements()) {
      final BpmnShape shape = findBpmnShape(element);
      if (shape != null) {
        final Bounds bounds = shape.getBounds();
        final double bottom = bounds.getY() + bounds.getHeight();
        if (bottom > lowestHeight) {
          lowestHeight = bottom;
        }
      }
    }

    return lowestHeight;
  }
}
