/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.gateway;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableExclusiveGateway;
import io.zeebe.engine.processor.workflow.handlers.element.ElementCompletedHandler;

public class ExclusiveGatewayElementCompletedHandler<T extends ExecutableExclusiveGateway>
    extends ElementCompletedHandler<T> {

  public ExclusiveGatewayElementCompletedHandler() {
    super();
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    publishDeferredRecords(context);
    return super.handleState(context);
  }
}
