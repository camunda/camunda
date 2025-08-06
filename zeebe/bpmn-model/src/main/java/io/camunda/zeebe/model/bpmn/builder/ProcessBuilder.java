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
import io.camunda.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import io.camunda.zeebe.model.bpmn.instance.dc.Bounds;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeVersionTag;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * @author Sebastian Menski
 */
public class ProcessBuilder extends AbstractProcessBuilder<ProcessBuilder>
    implements ZeebeExecutionListenersBuilder<ProcessBuilder>,
        ZeebePropertiesBuilder<ProcessBuilder> {

  private final ZeebeExecutionListenersBuilder<ProcessBuilder> zeebeExecutionListenersBuilder;
  private final ZeebePropertiesBuilder<ProcessBuilder> zeebePropertiesBuilder;

  public ProcessBuilder(final BpmnModelInstance modelInstance, final Process process) {
    super(modelInstance, process, ProcessBuilder.class);
    zeebeExecutionListenersBuilder = new ZeebeExecutionListenersBuilderImpl<>(myself);
    zeebePropertiesBuilder = new ZeebePropertiesBuilderImpl<>(myself);
  }

  public StartEventBuilder startEvent() {
    return startEvent(null);
  }

  public StartEventBuilder startEvent(final String id) {
    final StartEvent start = createChild(StartEvent.class, id);
    final BpmnShape bpmnShape = createBpmnShape(start);
    setCoordinates(bpmnShape);
    return start.builder();
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

  public ProcessBuilder eventSubProcess(
      final String id, final Consumer<EventSubProcessBuilder> consumer) {
    final EventSubProcessBuilder builder = eventSubProcess(id);
    consumer.accept(builder);
    return this;
  }

  public IntermediateCatchEventBuilder linkCatchEvent() {
    return linkCatchEvent(null);
  }

  public IntermediateCatchEventBuilder linkCatchEvent(final String id) {
    final IntermediateCatchEvent catchEvent = createChild(IntermediateCatchEvent.class, id);
    final BpmnShape bpmnShape = createBpmnShape(catchEvent);
    setEventCoordinates(bpmnShape);
    resizeBpmnShape(bpmnShape);
    return catchEvent.builder();
  }

  @Override
  protected void setCoordinates(final BpmnShape targetBpmnShape) {
    final Bounds bounds = targetBpmnShape.getBounds();
    bounds.setX(100);
    // Y coordinate is 36 lower than X. This is because the start event will have a height of 36.
    // This shape is already added to the model, so the getLowestHeight will add these 36 pixels.
    bounds.setY(64 + getLowestHeight());
  }

  protected void setEventCoordinates(final BpmnShape targetBpmnShape) {
    final Bounds targetBounds = targetBpmnShape.getBounds();
    final double yCoord = getLowestHeight() + 50.0;
    final double xCoord = 100.0;

    // move target
    targetBounds.setY(yCoord);
    targetBounds.setX(xCoord);
  }

  private double getLowestHeight() {
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
    return lowestheight;
  }

  @Override
  public ProcessBuilder zeebeStartExecutionListener(final String type, final String retries) {
    return zeebeExecutionListenersBuilder.zeebeStartExecutionListener(type, retries);
  }

  @Override
  public ProcessBuilder zeebeStartExecutionListener(final String type) {
    return zeebeExecutionListenersBuilder.zeebeStartExecutionListener(type);
  }

  @Override
  public ProcessBuilder zeebeEndExecutionListener(final String type, final String retries) {
    return zeebeExecutionListenersBuilder.zeebeEndExecutionListener(type, retries);
  }

  @Override
  public ProcessBuilder zeebeEndExecutionListener(final String type) {
    return zeebeExecutionListenersBuilder.zeebeEndExecutionListener(type);
  }

  @Override
  public ProcessBuilder zeebeExecutionListener(
      final Consumer<ExecutionListenerBuilder> executionListenerBuilderConsumer) {
    return zeebeExecutionListenersBuilder.zeebeExecutionListener(executionListenerBuilderConsumer);
  }

  @Override
  public ProcessBuilder zeebeProperty(final String name, final String value) {
    return zeebePropertiesBuilder.zeebeProperty(name, value);
  }

  public ProcessBuilder versionTag(final String value) {
    addExtensionElement(ZeebeVersionTag.class, versionTag -> versionTag.setValue(value));
    return this;
  }
}
