/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHocImplementationType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;
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

  private final Map<String, ExecutableFlowNode> adHocActivitiesById = new HashMap<>();

  private final DirectBuffer adHocActivitiesDescription = new UnsafeBuffer();

  public ExecutableAdHocSubProcess(final String id) {
    super(id);
    innerInstanceId = id + ZeebeConstants.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
  }

  public Expression getActiveElementsCollection() {
    return activeElementsCollection;
  }

  public void setActiveElementsCollection(final Expression activeElementsCollection) {
    this.activeElementsCollection = activeElementsCollection;
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

  public String getInnerInstanceId() {
    return innerInstanceId;
  }

  public DirectBuffer getAdHocActivitiesDescription() {
    return adHocActivitiesDescription;
  }

  public void setAdHocActivitiesDescription(final DirectBuffer description) {
    adHocActivitiesDescription.wrap(description);
  }
}
