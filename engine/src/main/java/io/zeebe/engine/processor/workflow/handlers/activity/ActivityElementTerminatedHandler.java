/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.activity;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.handlers.IncidentResolver;
import io.zeebe.engine.processor.workflow.handlers.element.ElementTerminatedHandler;
import io.zeebe.engine.state.instance.ElementInstance;

/**
 * Performs usual ElementTerminated logic and publishes any deferred record. At the moment, it will
 * always try to publish deferred tokens, even if there are none; this could be optimized by
 * checking {@link ElementInstance#getNumberOfActiveExecutionPaths()}
 *
 * @param <T>
 */
public class ActivityElementTerminatedHandler<T extends ExecutableActivity>
    extends ElementTerminatedHandler<T> {

  public ActivityElementTerminatedHandler(IncidentResolver incidentResolver) {
    super(incidentResolver);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    publishDeferredRecords(context);
    return super.handleState(context);
  }
}
