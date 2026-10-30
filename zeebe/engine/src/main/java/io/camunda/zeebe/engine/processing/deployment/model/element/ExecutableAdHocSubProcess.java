/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHocImplementationType;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExecutableAdHocSubProcess extends ExecutableFlowElementContainer
    implements ExecutableJobWorkerElement {

  private final String innerInstanceId;
  private Expression activeElementsCollection;
  private Expression completionCondition;
  private boolean cancelRemainingInstances;
  private ZeebeAdHocImplementationType implementationType;
  private JobWorkerProperties jobWorkerProperties;
  private AgentDefinitionType agentDefinitionType;

  private Optional<DirectBuffer> outputCollection = Optional.empty();
  private Optional<Expression> outputElement = Optional.empty();

  private final Map<String, ExecutableFlowNode> adHocActivitiesById = new HashMap<>();
  private final DirectBuffer adHocActivitiesMetadata = new UnsafeBuffer();

  // derived lazily from the model instead of in the (frozen) transformer, so that the shared joins
  // also apply to processes that were deployed before they were introduced; not volatile, as the
  // model is only used by the processing thread of the partition that cached it
  private Set<ExecutableFlowNode> sharedJoins;

  public ExecutableAdHocSubProcess(final String id) {
    super(id);
    innerInstanceId = id + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
    agentDefinitionType = AgentDefinitionType.UNSPECIFIED;
  }

  public Expression getActiveElementsCollection() {
    return activeElementsCollection;
  }

  public void setActiveElementsCollection(final Expression activeElementsCollection) {
    this.activeElementsCollection = activeElementsCollection;
  }

  public String getInnerInstanceId() {
    return innerInstanceId;
  }

  public Expression getCompletionCondition() {
    return completionCondition;
  }

  public void setCompletionCondition(final Expression completionCondition) {
    this.completionCondition = completionCondition;
  }

  public boolean isCancelRemainingInstances() {
    return cancelRemainingInstances;
  }

  public void setCancelRemainingInstances(final boolean cancelRemainingInstances) {
    this.cancelRemainingInstances = cancelRemainingInstances;
  }

  public Map<String, ExecutableFlowNode> getAdHocActivitiesById() {
    return adHocActivitiesById;
  }

  public void addAdHocActivity(final ExecutableFlowNode adHocActivity) {
    final String elementId = BufferUtil.bufferAsString(adHocActivity.getId());
    adHocActivitiesById.put(elementId, adHocActivity);
  }

  /**
   * Returns {@code true} if the element is a joining gateway that is reached from more than one
   * ad-hoc activity. Such a join lives in the ad-hoc sub-process instance instead of in an inner
   * instance, so that it can count the sequence flows coming from different inner instances.
   */
  public boolean isSharedJoin(final ExecutableFlowElement element) {
    var joins = sharedJoins;
    if (joins == null) {
      joins = AdHocSubProcessSharedJoins.compute(adHocActivitiesById.values());
      sharedJoins = joins;
    }
    return joins.contains(element);
  }

  public ZeebeAdHocImplementationType getImplementationType() {
    return implementationType;
  }

  public void setImplementationType(final ZeebeAdHocImplementationType implementationType) {
    this.implementationType = implementationType;
  }

  @Override
  public JobWorkerProperties getJobWorkerProperties() {
    return jobWorkerProperties;
  }

  @Override
  public void setJobWorkerProperties(final JobWorkerProperties jobWorkerProperties) {
    this.jobWorkerProperties = jobWorkerProperties;
  }

  @Override
  public AgentDefinitionType getAgentDefinitionType() {
    return agentDefinitionType;
  }

  @Override
  public void setAgentDefinitionType(final AgentDefinitionType agentDefinitionType) {
    this.agentDefinitionType = agentDefinitionType;
  }

  public DirectBuffer getAdHocActivitiesMetadata() {
    return adHocActivitiesMetadata;
  }

  public void setAdHocActivitiesMetadata(final DirectBuffer activitiesMetadata) {
    adHocActivitiesMetadata.wrap(activitiesMetadata);
  }

  public Optional<DirectBuffer> getOutputCollection() {
    return outputCollection;
  }

  public void setOutputCollection(final DirectBuffer outputCollection) {
    this.outputCollection = Optional.of(outputCollection);
  }

  public Optional<Expression> getOutputElement() {
    return outputElement;
  }

  public void setOutputElement(final Expression outputElement) {
    this.outputElement = Optional.of(outputElement);
  }
}
