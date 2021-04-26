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

import io.zeebe.model.bpmn.BpmnModelException;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.Association;
import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.zeebe.model.bpmn.instance.Definitions;
import io.zeebe.model.bpmn.instance.Error;
import io.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.zeebe.model.bpmn.instance.Escalation;
import io.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.zeebe.model.bpmn.instance.Event;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.ExtensionElements;
import io.zeebe.model.bpmn.instance.FlowElement;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.Gateway;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.model.bpmn.instance.Signal;
import io.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnEdge;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnPlane;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import io.zeebe.model.bpmn.instance.dc.Bounds;
import io.zeebe.model.bpmn.instance.di.Waypoint;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskForm;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Consumer;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/** @author Sebastian Menski */
public abstract class AbstractBaseElementBuilder<
        B extends AbstractBaseElementBuilder<B, E>, E extends BaseElement>
    extends AbstractBpmnModelElementBuilder<B, E> {

  public static final double SPACE = 50;

  private static final String ZEEBE_EXPRESSION_PREFIX = "=";
  public static final String ZEEBE_EXPRESSION_FORMAT = ZEEBE_EXPRESSION_PREFIX + "%s";

  protected AbstractBaseElementBuilder(
      final BpmnModelInstance modelInstance, final E element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  protected <T extends BpmnModelElementInstance> T createInstance(final Class<T> typeClass) {
    return modelInstance.newInstance(typeClass);
  }

  protected <T extends BaseElement> T createInstance(
      final Class<T> typeClass, final String identifier) {
    final T instance = createInstance(typeClass);
    if (identifier != null) {
      instance.setId(identifier);
      if (instance instanceof FlowElement) {
        ((FlowElement) instance).setName(identifier);
      }
    }
    return instance;
  }

  protected <T extends BpmnModelElementInstance> T createChild(final Class<T> typeClass) {
    return createChild(element, typeClass);
  }

  protected <T extends BaseElement> T createChild(
      final Class<T> typeClass, final String identifier) {
    return createChild(element, typeClass, identifier);
  }

  protected <T extends BpmnModelElementInstance> T createChild(
      final BpmnModelElementInstance parent, final Class<T> typeClass) {
    final T instance = createInstance(typeClass);
    parent.addChildElement(instance);
    return instance;
  }

  protected <T extends BaseElement> T createChild(
      final BpmnModelElementInstance parent, final Class<T> typeClass, final String identifier) {
    final T instance = createInstance(typeClass, identifier);
    parent.addChildElement(instance);
    return instance;
  }

  protected <T extends BpmnModelElementInstance> T createSibling(final Class<T> typeClass) {
    final T instance = createInstance(typeClass);
    element.getParentElement().addChildElement(instance);
    return instance;
  }

  protected <T extends BaseElement> T createSibling(
      final Class<T> typeClass, final String identifier) {
    final T instance = createInstance(typeClass, identifier);
    element.getParentElement().addChildElement(instance);
    return instance;
  }

  protected <T extends BpmnModelElementInstance> T getCreateSingleChild(final Class<T> typeClass) {
    return getCreateSingleChild(element, typeClass);
  }

  protected <T extends BpmnModelElementInstance> T getCreateSingleChild(
      final BpmnModelElementInstance parent, final Class<T> typeClass) {
    final Collection<T> childrenOfType = parent.getChildElementsByType(typeClass);
    if (childrenOfType.isEmpty()) {
      return createChild(parent, typeClass);
    } else {
      if (childrenOfType.size() > 1) {
        throw new BpmnModelException(
            "Element "
                + parent
                + " of type "
                + parent.getElementType().getTypeName()
                + " has more than one child element of type "
                + typeClass.getName());
      } else {
        return childrenOfType.iterator().next();
      }
    }
  }

  protected <T extends BpmnModelElementInstance> T getCreateSingleExtensionElement(
      final Class<T> typeClass) {
    final ExtensionElements extensionElements = getCreateSingleChild(ExtensionElements.class);
    return getCreateSingleChild(extensionElements, typeClass);
  }

  protected Message findMessageForName(final String messageName) {
    final Collection<Message> messages = modelInstance.getModelElementsByType(Message.class);
    for (final Message message : messages) {
      if (messageName.equals(message.getName())) {
        // return already existing message for message name
        return message;
      }
    }

    // create new message for non existing message name
    final Message message = createMessage();
    message.setName(messageName);

    return message;
  }

  protected Message createMessage() {
    final Definitions definitions = modelInstance.getDefinitions();
    final Message message = createChild(definitions, Message.class);
    return message;
  }

  protected MessageEventDefinition createMessageEventDefinition(final String messageName) {
    final Message message = findMessageForName(messageName);
    final MessageEventDefinition messageEventDefinition =
        createInstance(MessageEventDefinition.class);
    messageEventDefinition.setMessage(message);
    return messageEventDefinition;
  }

  protected MessageEventDefinition createEmptyMessageEventDefinition() {
    return createInstance(MessageEventDefinition.class);
  }

  protected Signal findSignalForName(final String signalName) {
    final Collection<Signal> signals = modelInstance.getModelElementsByType(Signal.class);
    for (final Signal signal : signals) {
      if (signalName.equals(signal.getName())) {
        // return already existing signal for signal name
        return signal;
      }
    }

    // create new signal for non existing signal name
    final Definitions definitions = modelInstance.getDefinitions();
    final Signal signal = createChild(definitions, Signal.class);
    signal.setName(signalName);

    return signal;
  }

  protected SignalEventDefinition createSignalEventDefinition(final String signalName) {
    final Signal signal = findSignalForName(signalName);
    final SignalEventDefinition signalEventDefinition = createInstance(SignalEventDefinition.class);
    signalEventDefinition.setSignal(signal);
    return signalEventDefinition;
  }

  protected ErrorEventDefinition findErrorDefinitionForCode(final String errorCode) {
    final Collection<ErrorEventDefinition> definitions =
        modelInstance.getModelElementsByType(ErrorEventDefinition.class);
    for (final ErrorEventDefinition definition : definitions) {
      final Error error = definition.getError();
      if (error != null && error.getErrorCode().equals(errorCode)) {
        return definition;
      }
    }
    return null;
  }

  protected Error findErrorForNameAndCode(final String errorCode) {
    final Collection<Error> errors = modelInstance.getModelElementsByType(Error.class);
    for (final Error error : errors) {
      if (errorCode.equals(error.getErrorCode())) {
        // return already existing error
        return error;
      }
    }

    // create new error
    final Definitions definitions = modelInstance.getDefinitions();
    final Error error = createChild(definitions, Error.class);
    error.setErrorCode(errorCode);

    return error;
  }

  protected ErrorEventDefinition createEmptyErrorEventDefinition() {
    final ErrorEventDefinition errorEventDefinition = createInstance(ErrorEventDefinition.class);
    return errorEventDefinition;
  }

  protected ErrorEventDefinition createErrorEventDefinition(final String errorCode) {
    final Error error = findErrorForNameAndCode(errorCode);
    final ErrorEventDefinition errorEventDefinition = createInstance(ErrorEventDefinition.class);
    errorEventDefinition.setError(error);
    return errorEventDefinition;
  }

  protected Escalation findEscalationForCode(final String escalationCode) {
    final Collection<Escalation> escalations =
        modelInstance.getModelElementsByType(Escalation.class);
    for (final Escalation escalation : escalations) {
      if (escalationCode.equals(escalation.getEscalationCode())) {
        // return already existing escalation
        return escalation;
      }
    }

    final Definitions definitions = modelInstance.getDefinitions();
    final Escalation escalation = createChild(definitions, Escalation.class);
    escalation.setEscalationCode(escalationCode);
    return escalation;
  }

  protected EscalationEventDefinition createEscalationEventDefinition(final String escalationCode) {
    final Escalation escalation = findEscalationForCode(escalationCode);
    final EscalationEventDefinition escalationEventDefinition =
        createInstance(EscalationEventDefinition.class);
    escalationEventDefinition.setEscalation(escalation);
    return escalationEventDefinition;
  }

  protected CompensateEventDefinition createCompensateEventDefinition() {
    final CompensateEventDefinition compensateEventDefinition =
        createInstance(CompensateEventDefinition.class);
    return compensateEventDefinition;
  }

  protected Process findProcess() {
    ModelElementInstance parentElement;
    do {
      parentElement = element.getParentElement();
    } while (!(parentElement == null || parentElement instanceof Process));

    if (parentElement == null) {
      throw new RuntimeException("Unable to find process parent for element " + element);
    }

    return (Process) parentElement;
  }

  protected ZeebeUserTaskForm createZeebeUserTaskForm() {
    final Process process = findProcess();
    final ExtensionElements extensionElements =
        getCreateSingleChild(process, ExtensionElements.class);
    return createChild(extensionElements, ZeebeUserTaskForm.class);
  }

  /**
   * Sets the identifier of the element.
   *
   * @param identifier the identifier to set
   * @return the builder object
   */
  public B id(final String identifier) {
    element.setId(identifier);
    return myself;
  }

  /**
   * Add an extension element to the element.
   *
   * @param extensionElement the extension element to add
   * @return the builder object
   */
  public B addExtensionElement(final BpmnModelElementInstance extensionElement) {
    final ExtensionElements extensionElements = getCreateSingleChild(ExtensionElements.class);
    extensionElements.addChildElement(extensionElement);
    return myself;
  }

  public <T extends BpmnModelElementInstance> B addExtensionElement(
      final Class<T> extensionClass, final Consumer<T> builder) {
    final T element = createInstance(extensionClass);
    builder.accept(element);
    return addExtensionElement(element);
  }

  protected String asZeebeExpression(final String expression) {
    if ((expression != null)
        && (!expression.isEmpty())
        && !(expression.startsWith(ZEEBE_EXPRESSION_PREFIX))) {
      return String.format(ZEEBE_EXPRESSION_FORMAT, expression);
    } else {
      return expression;
    }
  }

  public BpmnShape createBpmnShape(final FlowNode node) {
    final BpmnPlane bpmnPlane = findBpmnPlane();
    if (bpmnPlane != null) {
      final BpmnShape bpmnShape = createInstance(BpmnShape.class);
      bpmnShape.setBpmnElement(node);
      final Bounds nodeBounds = createInstance(Bounds.class);

      if (node instanceof SubProcess) {
        bpmnShape.setExpanded(true);
        nodeBounds.setWidth(350);
        nodeBounds.setHeight(200);
      } else if (node instanceof Activity) {
        nodeBounds.setWidth(100);
        nodeBounds.setHeight(80);
      } else if (node instanceof Event) {
        nodeBounds.setWidth(36);
        nodeBounds.setHeight(36);
      } else if (node instanceof Gateway) {
        nodeBounds.setWidth(50);
        nodeBounds.setHeight(50);
        if (node instanceof ExclusiveGateway) {
          bpmnShape.setMarkerVisible(true);
        }
      }

      nodeBounds.setX(0);
      nodeBounds.setY(0);

      bpmnShape.addChildElement(nodeBounds);
      bpmnPlane.addChildElement(bpmnShape);

      return bpmnShape;
    }
    return null;
  }

  protected void setCoordinates(final BpmnShape shape) {
    final BpmnShape source = findBpmnShape(element);
    final Bounds shapeBounds = shape.getBounds();

    double x = 0;
    double y = 0;

    if (source != null) {
      final Bounds sourceBounds = source.getBounds();

      final double sourceX = sourceBounds.getX();
      final double sourceWidth = sourceBounds.getWidth();
      x = sourceX + sourceWidth + SPACE;

      if (element instanceof FlowNode) {
        final FlowNode flowNode = (FlowNode) element;
        y = getFlowNodeYCoordinate(flowNode, shapeBounds, sourceBounds);
      }
    }

    shapeBounds.setX(x);
    shapeBounds.setY(y);
  }

  public BpmnEdge createEdge(final BaseElement baseElement) {
    final BpmnPlane bpmnPlane = findBpmnPlane();
    if (bpmnPlane != null) {

      final BpmnEdge edge = createInstance(BpmnEdge.class);
      edge.setBpmnElement(baseElement);
      setWaypoints(edge);

      bpmnPlane.addChildElement(edge);
      return edge;
    }
    return null;
  }

  protected void setWaypoints(final BpmnEdge edge) {
    final BaseElement bpmnElement = edge.getBpmnElement();

    final FlowNode edgeSource;
    final FlowNode edgeTarget;
    if (bpmnElement instanceof SequenceFlow) {

      final SequenceFlow sequenceFlow = (SequenceFlow) bpmnElement;

      edgeSource = sequenceFlow.getSource();
      edgeTarget = sequenceFlow.getTarget();

    } else if (bpmnElement instanceof Association) {
      final Association association = (Association) bpmnElement;

      edgeSource = (FlowNode) association.getSource();
      edgeTarget = (FlowNode) association.getTarget();
    } else {
      throw new RuntimeException("Bpmn element type not supported");
    }

    setWaypointsWithSourceAndTarget(edge, edgeSource, edgeTarget);
  }

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

      if (edgeSource.getOutgoing().size() == 1) {
        w1.setX(sourceX + sourceWidth);
        w1.setY(sourceY + sourceHeight / 2);

        edge.addChildElement(w1);
      } else {
        w1.setX(sourceX + sourceWidth / 2);
        w1.setY(sourceY + sourceHeight);

        edge.addChildElement(w1);

        final Waypoint w2 = createInstance(Waypoint.class);
        w2.setX(sourceX + sourceWidth / 2);
        w2.setY(targetY + targetHeight / 2);

        edge.addChildElement(w2);
      }

      final Waypoint w3 = createInstance(Waypoint.class);
      w3.setX(targetX);
      w3.setY(targetY + targetHeight / 2);

      edge.addChildElement(w3);
    }
  }

  protected BpmnPlane findBpmnPlane() {
    final Collection<BpmnPlane> planes = modelInstance.getModelElementsByType(BpmnPlane.class);
    return planes.iterator().next();
  }

  protected BpmnShape findBpmnShape(final BaseElement node) {
    final Collection<BpmnShape> allShapes = modelInstance.getModelElementsByType(BpmnShape.class);

    final Iterator<BpmnShape> iterator = allShapes.iterator();
    while (iterator.hasNext()) {
      final BpmnShape shape = iterator.next();
      if (shape.getBpmnElement().equals(node)) {
        return shape;
      }
    }
    return null;
  }

  protected BpmnEdge findBpmnEdge(final BaseElement sequenceFlow) {
    final Collection<BpmnEdge> allEdges = modelInstance.getModelElementsByType(BpmnEdge.class);
    final Iterator<BpmnEdge> iterator = allEdges.iterator();

    while (iterator.hasNext()) {
      final BpmnEdge edge = iterator.next();
      if (edge.getBpmnElement().equals(sequenceFlow)) {
        return edge;
      }
    }
    return null;
  }

  protected void resizeSubProcess(final BpmnShape innerShape) {

    BaseElement innerElement = innerShape.getBpmnElement();
    Bounds innerShapeBounds = innerShape.getBounds();

    ModelElementInstance parent = innerElement.getParentElement();

    while (parent instanceof SubProcess) {

      final BpmnShape subProcessShape = findBpmnShape((SubProcess) parent);

      if (subProcessShape != null) {

        final Bounds subProcessBounds = subProcessShape.getBounds();
        final double innerX = innerShapeBounds.getX();
        final double innerWidth = innerShapeBounds.getWidth();
        final double innerY = innerShapeBounds.getY();
        final double innerHeight = innerShapeBounds.getHeight();

        final double subProcessY = subProcessBounds.getY();
        final double subProcessHeight = subProcessBounds.getHeight();
        final double subProcessX = subProcessBounds.getX();
        final double subProcessWidth = subProcessBounds.getWidth();

        final double tmpWidth = innerX + innerWidth + SPACE;
        final double tmpHeight = innerY + innerHeight + SPACE;

        if (innerY == subProcessY) {
          subProcessBounds.setY(subProcessY - SPACE);
          subProcessBounds.setHeight(subProcessHeight + SPACE);
        }

        if (tmpWidth >= subProcessX + subProcessWidth) {
          final double newWidth = tmpWidth - subProcessX;
          subProcessBounds.setWidth(newWidth);
        }

        if (tmpHeight >= subProcessY + subProcessHeight) {
          final double newHeight = tmpHeight - subProcessY;
          subProcessBounds.setHeight(newHeight);
        }

        innerElement = (SubProcess) parent;
        innerShapeBounds = subProcessBounds;
        parent = innerElement.getParentElement();
      } else {
        break;
      }
    }
  }

  private double getFlowNodeYCoordinate(
      final FlowNode flowNode, final Bounds shapeBounds, final Bounds sourceBounds) {
    final Collection<SequenceFlow> outgoing = flowNode.getOutgoing();
    double y = 0;

    if (outgoing.size() == 0) {
      final double sourceY = sourceBounds.getY();
      final double sourceHeight = sourceBounds.getHeight();
      final double targetHeight = shapeBounds.getHeight();
      y = sourceY + sourceHeight / 2 - targetHeight / 2;
    } else {
      final SequenceFlow[] sequenceFlows = outgoing.toArray(new SequenceFlow[outgoing.size()]);
      final SequenceFlow last = sequenceFlows[outgoing.size() - 1];
      final BpmnShape targetShape = findBpmnShape(last.getTarget());

      if (targetShape != null) {
        final Bounds targetBounds = targetShape.getBounds();
        final double lastY = targetBounds.getY();
        final double lastHeight = targetBounds.getHeight();
        y = lastY + lastHeight + SPACE;
      }
    }

    return y;
  }
}
