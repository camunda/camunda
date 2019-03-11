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

import static io.zeebe.model.bpmn.builder.AbstractBaseElementBuilder.SPACE;

import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import io.zeebe.model.bpmn.instance.dc.Bounds;

/** @author Sebastian Menski */
public class EmbeddedSubProcessBuilder
    extends AbstractEmbeddedSubProcessBuilder<
        EmbeddedSubProcessBuilder, AbstractSubProcessBuilder<?>> {

  @SuppressWarnings("rawtypes")
  protected EmbeddedSubProcessBuilder(AbstractSubProcessBuilder subProcessBuilder) {
    super(subProcessBuilder, EmbeddedSubProcessBuilder.class);
  }

  public StartEventBuilder startEvent() {
    return startEvent(null);
  }

  public StartEventBuilder startEvent(String id) {
    final StartEvent start = subProcessBuilder.createChild(StartEvent.class, id);

    final BpmnShape startShape = subProcessBuilder.createBpmnShape(start);
    final BpmnShape subProcessShape =
        subProcessBuilder.findBpmnShape(subProcessBuilder.getElement());

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

  public EventSubProcessBuilder eventSubProcess() {
    return eventSubProcess(null);
  }

  public EventSubProcessBuilder eventSubProcess(String id) {
    // Create a subprocess, triggered by an event, and add it to modelInstance
    final SubProcess subProcess = subProcessBuilder.createChild(SubProcess.class, id);
    subProcess.setTriggeredByEvent(true);

    // Create Bpmn shape so subprocess will be drawn
    final BpmnShape targetBpmnShape = subProcessBuilder.createBpmnShape(subProcess);
    // find the lowest shape in the process
    // place event sub process underneath
    setCoordinates(targetBpmnShape);

    subProcessBuilder.resizeSubProcess(targetBpmnShape);

    // Return the eventSubProcessBuilder
    final EventSubProcessBuilder eventSubProcessBuilder =
        new EventSubProcessBuilder(subProcessBuilder.modelInstance, subProcess);
    return eventSubProcessBuilder;
  }

  protected void setCoordinates(BpmnShape targetBpmnShape) {

    final SubProcess eventSubProcess = (SubProcess) targetBpmnShape.getBpmnElement();
    final SubProcess parentSubProcess = (SubProcess) eventSubProcess.getParentElement();
    final BpmnShape parentBpmnShape = subProcessBuilder.findBpmnShape(parentSubProcess);

    final Bounds targetBounds = targetBpmnShape.getBounds();
    final Bounds parentBounds = parentBpmnShape.getBounds();

    // these should just be offsets maybe
    final double ycoord = parentBounds.getHeight() + parentBounds.getY();

    double xcoord =
        (parentBounds.getWidth() / 2) - (targetBounds.getWidth() / 2) + parentBounds.getX();
    if (xcoord - parentBounds.getX() < 50.0) {
      xcoord = 50.0 + parentBounds.getX();
    }

    // move target
    targetBounds.setY(ycoord);
    targetBounds.setX(xcoord);

    // parent expands automatically

    // nodes surrounding the parent subprocess will not be moved
    // they may end up inside the subprocess (but only graphically)
  }
}
