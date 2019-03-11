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
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import io.zeebe.model.bpmn.instance.dc.Bounds;
import java.util.Collection;

/** @author Sebastian Menski */
public class ProcessBuilder extends AbstractProcessBuilder<ProcessBuilder> {

  public ProcessBuilder(BpmnModelInstance modelInstance, Process process) {
    super(modelInstance, process, ProcessBuilder.class);
  }

  public StartEventBuilder startEvent() {
    return startEvent(null);
  }

  public StartEventBuilder startEvent(String id) {
    final StartEvent start = createChild(StartEvent.class, id);
    final BpmnShape bpmnShape = createBpmnShape(start);
    setCoordinates(bpmnShape);
    return start.builder();
  }

  public EventSubProcessBuilder eventSubProcess() {
    return eventSubProcess(null);
  }

  public EventSubProcessBuilder eventSubProcess(String id) {
    // Create a subprocess, triggered by an event, and add it to modelInstance
    final SubProcess subProcess = createChild(SubProcess.class, id);
    subProcess.setTriggeredByEvent(true);

    // Create Bpmn shape so subprocess will be drawn
    final BpmnShape targetBpmnShape = createBpmnShape(subProcess);
    // find the lowest shape in the process
    // place event sub process underneath
    setEventSubProcessCoordinates(targetBpmnShape);

    resizeSubProcess(targetBpmnShape);

    // Return the eventSubProcessBuilder
    final EventSubProcessBuilder eventSubProcessBuilder =
        new EventSubProcessBuilder(modelInstance, subProcess);
    return eventSubProcessBuilder;
  }

  @Override
  protected void setCoordinates(BpmnShape targetBpmnShape) {
    final Bounds bounds = targetBpmnShape.getBounds();
    bounds.setX(100);
    bounds.setY(100);
  }

  protected void setEventSubProcessCoordinates(BpmnShape targetBpmnShape) {
    final SubProcess eventSubProcess = (SubProcess) targetBpmnShape.getBpmnElement();
    final Bounds targetBounds = targetBpmnShape.getBounds();
    double lowestheight = 0;

    // find the lowest element in the model
    final Collection<BpmnShape> allShapes = modelInstance.getModelElementsByType(BpmnShape.class);
    for (final BpmnShape shape : allShapes) {
      final Bounds bounds = shape.getBounds();
      final double bottom = bounds.getY() + bounds.getHeight();
      if (bottom > lowestheight) {
        lowestheight = bottom;
      }
    }

    final double ycoord = lowestheight + 50.0;
    final double xcoord = 100.0;

    // move target
    targetBounds.setY(ycoord);
    targetBounds.setX(xcoord);
  }
}
