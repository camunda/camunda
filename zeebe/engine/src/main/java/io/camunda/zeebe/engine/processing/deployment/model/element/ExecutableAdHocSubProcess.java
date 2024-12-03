/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import java.util.ArrayList;
import java.util.List;

public class ExecutableAdHocSubProcess extends ExecutableFlowElementContainer {

  private final List<ExecutableFlowNode> adHocActivities = new ArrayList<>();

  public ExecutableAdHocSubProcess(final String id) {
    super(id);
  }

  public List<ExecutableFlowNode> getAdHocActivities() {
    return adHocActivities;
  }

  public void addAdHocActivity(final ExecutableFlowNode adHocActivity) {
    adHocActivities.add(adHocActivity);
  }
}
