/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;

public class ExecutableAdHocSubProcess extends ExecutableFlowElementContainer {

  private Expression activeElementsCollection;
  private Expression completionCondition;
  private boolean cancelRemainingInstancesEnabled;

  private final Map<String, ExecutableFlowNode> adHocActivitiesById = new HashMap<>();

  public ExecutableAdHocSubProcess(final String id) {
    super(id);
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

  public boolean isCancelRemainingInstancesEnabled() {
    return cancelRemainingInstancesEnabled;
  }

  public void setCancelRemainingInstancesEnabled(final boolean cancelRemainingInstancesEnabled) {
    this.cancelRemainingInstancesEnabled = cancelRemainingInstancesEnabled;
  }

  public Map<String, ExecutableFlowNode> getAdHocActivitiesById() {
    return adHocActivitiesById;
  }

  public void addAdHocActivity(final ExecutableFlowNode adHocActivity) {
    final String elementId = BufferUtil.bufferAsString(adHocActivity.getId());
    adHocActivitiesById.put(elementId, adHocActivity);
  }
}
