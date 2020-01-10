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
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnEdge;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import io.zeebe.model.bpmn.instance.dc.Bounds;
import io.zeebe.model.bpmn.instance.di.Waypoint;

/** @author Sebastian Menski */
public abstract class AbstractBoundaryEventBuilder<B extends AbstractBoundaryEventBuilder<B>>
    extends AbstractCatchEventBuilder<B, BoundaryEvent> {

  protected AbstractBoundaryEventBuilder(
      final BpmnModelInstance modelInstance, final BoundaryEvent element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Set if the boundary event cancels the attached activity.
   *
   * @param cancelActivity true if the boundary event cancels the activiy, false otherwise
   * @return the builder object
   */
  public B cancelActivity(final Boolean cancelActivity) {
    element.setCancelActivity(cancelActivity);

    return myself;
  }

  /**
   * Sets a catch all error definition.
   *
   * @return the builder object
   */
  public B error() {
    final ErrorEventDefinition errorEventDefinition = createInstance(ErrorEventDefinition.class);
    element.getEventDefinitions().add(errorEventDefinition);

    return myself;
  }

  /**
   * Sets an error definition for the given error code. If already an error with this code exists it
   * will be used, otherwise a new error is created.
   *
   * @param errorCode the code of the error
   * @return the builder object
   */
  public B error(final String errorCode) {
    final ErrorEventDefinition errorEventDefinition = createErrorEventDefinition(errorCode);
    element.getEventDefinitions().add(errorEventDefinition);

    return myself;
  }

  /**
   * Creates an error event definition with an unique id and returns a builder for the error event
   * definition.
   *
   * @return the error event definition builder object
   */
  public ErrorEventDefinitionBuilder errorEventDefinition(final String id) {
    final ErrorEventDefinition errorEventDefinition = createEmptyErrorEventDefinition();
    if (id != null) {
      errorEventDefinition.setId(id);
    }

    element.getEventDefinitions().add(errorEventDefinition);
    return new ErrorEventDefinitionBuilder(modelInstance, errorEventDefinition);
  }

  /**
   * Creates an error event definition and returns a builder for the error event definition.
   *
   * @return the error event definition builder object
   */
  public ErrorEventDefinitionBuilder errorEventDefinition() {
    final ErrorEventDefinition errorEventDefinition = createEmptyErrorEventDefinition();
    element.getEventDefinitions().add(errorEventDefinition);
    return new ErrorEventDefinitionBuilder(modelInstance, errorEventDefinition);
  }

  /**
   * Sets a catch all escalation definition.
   *
   * @return the builder object
   */
  public B escalation() {
    final EscalationEventDefinition escalationEventDefinition =
        createInstance(EscalationEventDefinition.class);
    element.getEventDefinitions().add(escalationEventDefinition);

    return myself;
  }

  /**
   * Sets an escalation definition for the given escalation code. If already an escalation with this
   * code exists it will be used, otherwise a new escalation is created.
   *
   * @param escalationCode the code of the escalation
   * @return the builder object
   */
  public B escalation(final String escalationCode) {
    final EscalationEventDefinition escalationEventDefinition =
        createEscalationEventDefinition(escalationCode);
    element.getEventDefinitions().add(escalationEventDefinition);

    return myself;
  }

  @Override
  protected void setCoordinates(final BpmnShape shape) {
    final BpmnShape source = findBpmnShape(element);
    final Bounds shapeBounds = shape.getBounds();

    double x = 0;
    double y = 0;

    if (source != null) {
      final Bounds sourceBounds = source.getBounds();

      final double sourceX = sourceBounds.getX();
      final double sourceWidth = sourceBounds.getWidth();
      final double sourceY = sourceBounds.getY();
      final double sourceHeight = sourceBounds.getHeight();
      final double targetHeight = shapeBounds.getHeight();

      x = sourceX + sourceWidth + SPACE / 4;
      y = sourceY + sourceHeight - targetHeight / 2 + SPACE;
    }

    shapeBounds.setX(x);
    shapeBounds.setY(y);
  }

  @Override
  protected void setWaypointsWithSourceAndTarget(
      final BpmnEdge edge, final FlowNode edgeSource, final FlowNode edgeTarget) {
    final BpmnShape source = findBpmnShape(edgeSource);
    final BpmnShape target = findBpmnShape(edgeTarget);

    if (source != null && target != null) {
      final Bounds sourceBounds = source.getBounds();
      final Bounds targetBounds = target.getBounds();

      final double sourceX = sourceBounds.getX();
      final double sourceY = sourceBounds.getY();
      final double sourceWidth = sourceBounds.getWidth();
      final double sourceHeight = sourceBounds.getHeight();

      final double targetX = targetBounds.getX();
      final double targetY = targetBounds.getY();
      final double targetHeight = targetBounds.getHeight();

      final Waypoint w1 = createInstance(Waypoint.class);
      w1.setX(sourceX + sourceWidth / 2);
      w1.setY(sourceY + sourceHeight);

      final Waypoint w2 = createInstance(Waypoint.class);
      w2.setX(sourceX + sourceWidth / 2);
      w2.setY(sourceY + sourceHeight + SPACE);

      final Waypoint w3 = createInstance(Waypoint.class);
      w3.setX(targetX);
      w3.setY(targetY + targetHeight / 2);

      edge.addChildElement(w1);
      edge.addChildElement(w2);
      edge.addChildElement(w3);
    }
  }
}
