/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;

public final class ExecutableBusinessRuleTask extends ExecutableJobWorkerTask
    implements ExecutableCalledDecision {

  private Expression decisionId;
  private String resultVariable;
  private ZeebeBindingType bindingType;
  private String versionTag;

  public ExecutableBusinessRuleTask(final String id) {
    super(id);
  }

  @Override
  public Expression getDecisionId() {
    return decisionId;
  }

  @Override
  public void setDecisionId(final Expression decisionId) {
    this.decisionId = decisionId;
  }

  @Override
  public String getResultVariable() {
    return resultVariable;
  }

  @Override
  public void setResultVariable(final String resultVariable) {
    this.resultVariable = resultVariable;
  }

  @Override
  public ZeebeBindingType getBindingType() {
    return bindingType;
  }

  @Override
  public void setBindingType(final ZeebeBindingType bindingType) {
    this.bindingType = bindingType;
  }

  @Override
  public String getVersionTag() {
    return versionTag;
  }

  @Override
  public void setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
  }
}
