/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

public class ExecutableCompensation extends AbstractFlowElement {

  private ExecutableActivity compensationHandler;

  /* Activity that could be referenced by a compensation throw event via attribute "activityRef".
   */
  private ExecutableActivity referenceCompensationActivity;

  public ExecutableCompensation(final String id) {
    super(id);
  }

  public ExecutableActivity getCompensationHandler() {
    return compensationHandler;
  }

  public void setCompensationHandler(final ExecutableActivity compensationHandler) {
    this.compensationHandler = compensationHandler;
  }

  public ExecutableActivity getReferenceCompensationActivity() {
    return referenceCompensationActivity;
  }

  public void setReferenceCompensationActivity(
      final ExecutableActivity referenceCompensationActivity) {
    this.referenceCompensationActivity = referenceCompensationActivity;
  }

  public boolean hasReferenceActivity() {
    return referenceCompensationActivity != null;
  }
}
