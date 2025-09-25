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

/** A representation of an element that calls a decision. For example, a business rule task. */
public interface ExecutableCalledDecision {

  Expression getDecisionId();

  void setDecisionId(Expression decisionId);

  String getResultVariable();

  void setResultVariable(String resultVariable);

  ZeebeBindingType getBindingType();

  void setBindingType(ZeebeBindingType bindingType);

  String getVersionTag();

  void setVersionTag(String versionTag);
}
