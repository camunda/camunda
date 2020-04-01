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

import io.zeebe.model.bpmn.AssociationDirection;
import io.zeebe.model.bpmn.BpmnModelException;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.Association;
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.zeebe.model.bpmn.instance.CallActivity;
import io.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.EndEvent;
import io.zeebe.model.bpmn.instance.EventBasedGateway;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.Gateway;
import io.zeebe.model.bpmn.instance.InclusiveGateway;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.zeebe.model.bpmn.instance.ManualTask;
import io.zeebe.model.bpmn.instance.ParallelGateway;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.ScriptTask;
import io.zeebe.model.bpmn.instance.SendTask;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.model.bpmn.instance.Transaction;
import io.zeebe.model.bpmn.instance.UserTask;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import java.util.function.Consumer;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

/** @author Sebastian Menski */
public abstract class AbstractFlowNodeBuilder<
        B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode>
    extends AbstractFlowElementBuilder<B, E> {

  protected boolean compensationStarted;
  protected BoundaryEvent compensateBoundaryEvent;
  private SequenceFlowBuilder currentSequenceFlowBuilder;

  protected AbstractFlowNodeBuilder(
      final BpmnModelInstance modelInstance, final E element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  protected SequenceFlowBuilder getCurrentSequenceFlowBuilder() {
    if (currentSequenceFlowBuilder == null) {
      final SequenceFlow sequenceFlow = createSibling(SequenceFlow.class);
      currentSequenceFlowBuilder = sequenceFlow.builder();
    }
    return currentSequenceFlowBuilder;
  }

  public B condition(final String name, final String condition) {
    if (name != null) {
      getCurrentSequenceFlowBuilder().name(name);
    }
    final ConditionExpression conditionExpression = createInstance(ConditionExpression.class);
    conditionExpression.setTextContent(condition);
    getCurrentSequenceFlowBuilder().condition(conditionExpression);
    return myself;
  }

  public B condition(final String condition) {
    return condition(null, condition);
  }

  public B conditionExpression(final String conditionExpression) {
    return condition(null, asZeebeExpression(conditionExpression));
  }

  protected void connectTarget(final FlowNode target) {
    // check if compensation was started
    if (isBoundaryEventWithStartedCompensation()) {
      // the target activity should be marked for compensation
      if (target instanceof Activity) {
        ((Activity) target).setForCompensation(true);
      }

      // connect the target via association instead of sequence flow
      connectTargetWithAssociation(target);
    } else if (isCompensationHandler()) {
      // cannot connect to a compensation handler
      throw new BpmnModelException(
          "Only single compensation handler allowed. Call compensationDone() to continue main flow.");
    } else {
      // connect as sequence flow by default
      connectTargetWithSequenceFlow(target);
    }
  }

  protected void connectTargetWithSequenceFlow(final FlowNode target) {
    getCurrentSequenceFlowBuilder().from(element).to(target);

    final SequenceFlow sequenceFlow = getCurrentSequenceFlowBuilder().getElement();
    createEdge(sequenceFlow);
    currentSequenceFlowBuilder = null;
  }

  protected void connectTargetWithAssociation(final FlowNode target) {
    final Association association = modelInstance.newInstance(Association.class);
    association.setTarget(target);
    association.setSource(element);
    association.setAssociationDirection(AssociationDirection.One);
    element.getParentElement().addChildElement(association);

    createEdge(association);
  }

  public AbstractFlowNodeBuilder compensationDone() {
    if (compensateBoundaryEvent != null) {
      return compensateBoundaryEvent.getAttachedTo().builder();
    } else {
      throw new BpmnModelException("No compensation in progress. Call compensationStart() first.");
    }
  }

  public B sequenceFlowId(final String sequenceFlowId) {
    getCurrentSequenceFlowBuilder().id(sequenceFlowId);
    return myself;
  }

  private <T extends FlowNode> T createTarget(final Class<T> typeClass) {
    return createTarget(typeClass, null);
  }

  protected <T extends FlowNode> T createTarget(final Class<T> typeClass, final String identifier) {
    final T target = createSibling(typeClass, identifier);

    final BpmnShape targetBpmnShape = createBpmnShape(target);
    setCoordinates(targetBpmnShape);
    connectTarget(target);
    resizeSubProcess(targetBpmnShape);
    return target;
  }

  protected <T extends AbstractFlowNodeBuilder, F extends FlowNode> T createTargetBuilder(
      final Class<F> typeClass) {
    return createTargetBuilder(typeClass, null);
  }

  protected <T extends AbstractFlowNodeBuilder, F extends FlowNode> T createTargetBuilder(
      final Class<F> typeClass, final String id) {
    final AbstractFlowNodeBuilder builder = createTarget(typeClass, id).builder();

    if (compensationStarted) {
      // pass on current boundary event to return after compensationDone call
      builder.compensateBoundaryEvent = compensateBoundaryEvent;
    }

    return (T) builder;
  }

  public ServiceTaskBuilder serviceTask() {
    return createTargetBuilder(ServiceTask.class);
  }

  public ServiceTaskBuilder serviceTask(final String id) {
    return createTargetBuilder(ServiceTask.class, id);
  }

  public ServiceTaskBuilder serviceTask(
      final String id, final Consumer<ServiceTaskBuilder> consumer) {
    final ServiceTaskBuilder builder = createTargetBuilder(ServiceTask.class, id);
    consumer.accept(builder);
    return builder;
  }

  public SendTaskBuilder sendTask() {
    return createTargetBuilder(SendTask.class);
  }

  public SendTaskBuilder sendTask(final String id) {
    return createTargetBuilder(SendTask.class, id);
  }

  public UserTaskBuilder userTask() {
    return createTargetBuilder(UserTask.class);
  }

  public UserTaskBuilder userTask(final String id) {
    return createTargetBuilder(UserTask.class, id);
  }

  public BusinessRuleTaskBuilder businessRuleTask() {
    return createTargetBuilder(BusinessRuleTask.class);
  }

  public BusinessRuleTaskBuilder businessRuleTask(final String id) {
    return createTargetBuilder(BusinessRuleTask.class, id);
  }

  public ScriptTaskBuilder scriptTask() {
    return createTargetBuilder(ScriptTask.class);
  }

  public ScriptTaskBuilder scriptTask(final String id) {
    return createTargetBuilder(ScriptTask.class, id);
  }

  public ReceiveTaskBuilder receiveTask() {
    return createTargetBuilder(ReceiveTask.class);
  }

  public ReceiveTaskBuilder receiveTask(final String id) {
    return createTargetBuilder(ReceiveTask.class, id);
  }

  public ReceiveTaskBuilder receiveTask(
      final String id, final Consumer<ReceiveTaskBuilder> consumer) {
    final ReceiveTaskBuilder builder = createTargetBuilder(ReceiveTask.class, id);
    consumer.accept(builder);
    return builder;
  }

  public ManualTaskBuilder manualTask() {
    return createTargetBuilder(ManualTask.class);
  }

  public ManualTaskBuilder manualTask(final String id) {
    return createTargetBuilder(ManualTask.class, id);
  }

  public EndEventBuilder endEvent() {
    return createTarget(EndEvent.class).builder();
  }

  public EndEventBuilder endEvent(final String id) {
    return createTarget(EndEvent.class, id).builder();
  }

  public EndEventBuilder endEvent(final String id, final Consumer<EndEventBuilder> consumer) {
    final EndEventBuilder builder = endEvent(id);
    consumer.accept(builder);
    return builder;
  }

  public ParallelGatewayBuilder parallelGateway() {
    return createTarget(ParallelGateway.class).builder();
  }

  public ParallelGatewayBuilder parallelGateway(final String id) {
    return createTarget(ParallelGateway.class, id).builder();
  }

  public ExclusiveGatewayBuilder exclusiveGateway() {
    return createTarget(ExclusiveGateway.class).builder();
  }

  public InclusiveGatewayBuilder inclusiveGateway() {
    return createTarget(InclusiveGateway.class).builder();
  }

  public EventBasedGatewayBuilder eventBasedGateway() {
    return createTarget(EventBasedGateway.class).builder();
  }

  public EventBasedGatewayBuilder eventBasedGateway(final String id) {
    return createTarget(EventBasedGateway.class, id).builder();
  }

  public ExclusiveGatewayBuilder exclusiveGateway(final String id) {
    return createTarget(ExclusiveGateway.class, id).builder();
  }

  public InclusiveGatewayBuilder inclusiveGateway(final String id) {
    return createTarget(InclusiveGateway.class, id).builder();
  }

  public IntermediateCatchEventBuilder intermediateCatchEvent() {
    return createTarget(IntermediateCatchEvent.class).builder();
  }

  public IntermediateCatchEventBuilder intermediateCatchEvent(final String id) {
    return createTarget(IntermediateCatchEvent.class, id).builder();
  }

  public IntermediateCatchEventBuilder intermediateCatchEvent(
      final String id, final Consumer<IntermediateCatchEventBuilder> builderConsumer) {
    final IntermediateCatchEventBuilder builder =
        createTarget(IntermediateCatchEvent.class, id).builder();
    builderConsumer.accept(builder);
    return builder;
  }

  public IntermediateThrowEventBuilder intermediateThrowEvent() {
    return createTarget(IntermediateThrowEvent.class).builder();
  }

  public IntermediateThrowEventBuilder intermediateThrowEvent(final String id) {
    return createTarget(IntermediateThrowEvent.class, id).builder();
  }

  public CallActivityBuilder callActivity() {
    return createTarget(CallActivity.class).builder();
  }

  public CallActivityBuilder callActivity(final String id) {
    return createTarget(CallActivity.class, id).builder();
  }

  public CallActivityBuilder callActivity(
      final String id, final Consumer<CallActivityBuilder> consumer) {
    final CallActivityBuilder builder = createTarget(CallActivity.class, id).builder();
    consumer.accept(builder);
    return builder;
  }

  public SubProcessBuilder subProcess() {
    return createTarget(SubProcess.class).builder();
  }

  public SubProcessBuilder subProcess(final String id) {
    return createTarget(SubProcess.class, id).builder();
  }

  public SubProcessBuilder subProcess(final String id, final Consumer<SubProcessBuilder> consumer) {
    final SubProcessBuilder builder = createTarget(SubProcess.class, id).builder();
    consumer.accept(builder);
    return builder;
  }

  public TransactionBuilder transaction() {
    final Transaction transaction = createTarget(Transaction.class);
    return new TransactionBuilder(modelInstance, transaction);
  }

  public TransactionBuilder transaction(final String id) {
    final Transaction transaction = createTarget(Transaction.class, id);
    return new TransactionBuilder(modelInstance, transaction);
  }

  private <T extends Gateway> T findLastGateway(final Class<T> gatewayType) {
    FlowNode lastGateway = element;
    while (true) {
      try {
        lastGateway = lastGateway.getPreviousNodes().singleResult();
        if (gatewayType.isAssignableFrom(lastGateway.getClass())) {
          return gatewayType.cast(lastGateway);
        }
      } catch (final BpmnModelException e) {
        throw new BpmnModelException(
            "Unable to determine an unique previous gateway of " + lastGateway.getId(), e);
      }
    }
  }

  public AbstractGatewayBuilder<?, ?> moveToLastGateway() {
    return findLastGateway(Gateway.class).builder();
  }

  public AbstractExclusiveGatewayBuilder<?> moveToLastExclusiveGateway() {
    return findLastGateway(ExclusiveGateway.class).builder();
  }

  public AbstractFlowNodeBuilder<?, ?> moveToNode(final String identifier) {
    final ModelElementInstance instance = modelInstance.getModelElementById(identifier);
    if (instance instanceof FlowNode) {
      return ((FlowNode) instance).builder();
    } else {
      throw new BpmnModelException("Flow node not found for id " + identifier);
    }
  }

  public <T extends AbstractActivityBuilder<?, ?>> T moveToActivity(final String identifier) {
    final ModelElementInstance instance = modelInstance.getModelElementById(identifier);
    if (instance instanceof Activity) {
      return (T) ((Activity) instance).builder();
    } else {
      throw new BpmnModelException("Activity not found for id " + identifier);
    }
  }

  public AbstractFlowNodeBuilder<?, ?> connectTo(final String identifier) {
    final ModelElementInstance target = modelInstance.getModelElementById(identifier);
    if (target == null) {
      throw new BpmnModelException(
          "Unable to connect "
              + element.getId()
              + " to element "
              + identifier
              + " cause it not exists.");
    } else if (!(target instanceof FlowNode)) {
      throw new BpmnModelException(
          "Unable to connect "
              + element.getId()
              + " to element "
              + identifier
              + " cause its not a flow node.");
    } else {
      final FlowNode targetNode = (FlowNode) target;
      connectTarget(targetNode);
      return targetNode.builder();
    }
  }

  public B compensationStart() {
    if (element instanceof BoundaryEvent) {
      final BoundaryEvent boundaryEvent = (BoundaryEvent) element;
      for (final EventDefinition eventDefinition : boundaryEvent.getEventDefinitions()) {
        if (eventDefinition instanceof CompensateEventDefinition) {
          // if the boundary event contains a compensate event definition then
          // save the boundary event to later return to it and start a compensation

          compensateBoundaryEvent = boundaryEvent;
          compensationStarted = true;

          return myself;
        }
      }
    }

    throw new BpmnModelException(
        "Compensation can only be started on a boundary event with a compensation event definition");
  }

  protected boolean isBoundaryEventWithStartedCompensation() {
    return compensationStarted && compensateBoundaryEvent != null;
  }

  protected boolean isCompensationHandler() {
    return !compensationStarted && compensateBoundaryEvent != null;
  }
}
