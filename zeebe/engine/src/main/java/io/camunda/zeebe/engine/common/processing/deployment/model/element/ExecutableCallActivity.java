/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;

public class ExecutableCallActivity extends ExecutableActivity {

  private Expression calledElementProcessId;

  private boolean propagateAllChildVariablesEnabled;
  private boolean propagateAllParentVariablesEnabled;

  /**
   * The index of this call activity element ID in a <i>lexicographically</i> sorted list of all
   * Call Activity IDs in all the processes in one BPMN deployment resource.
   */
  private int lexicographicIndex;

  private ZeebeBindingType bindingType;
  private String versionTag;

  public ExecutableCallActivity(final String id) {
    super(id);
  }

  public Expression getCalledElementProcessId() {
    return calledElementProcessId;
  }

  public void setCalledElementProcessId(final Expression calledElementProcessIdExpression) {
    calledElementProcessId = calledElementProcessIdExpression;
  }

  public boolean isPropagateAllChildVariablesEnabled() {
    return propagateAllChildVariablesEnabled;
  }

  public void setPropagateAllChildVariablesEnabled(
      final boolean propagateAllChildVariablesEnabled) {
    this.propagateAllChildVariablesEnabled = propagateAllChildVariablesEnabled;
  }

  public boolean isPropagateAllParentVariablesEnabled() {
    return propagateAllParentVariablesEnabled;
  }

  public void setPropagateAllParentVariablesEnabled(
      final boolean propagateAllParentVariablesEnabled) {
    this.propagateAllParentVariablesEnabled = propagateAllParentVariablesEnabled;
  }

  public int getLexicographicIndex() {
    return lexicographicIndex;
  }

  public void setLexicographicIndex(final int index) {
    lexicographicIndex = index;
  }

  public ZeebeBindingType getBindingType() {
    return bindingType;
  }

  public void setBindingType(final ZeebeBindingType bindingType) {
    this.bindingType = bindingType;
  }

  public String getVersionTag() {
    return versionTag;
  }

  public void setVersionTag(final String versionTag) {
    this.versionTag = versionTag;
  }
}
