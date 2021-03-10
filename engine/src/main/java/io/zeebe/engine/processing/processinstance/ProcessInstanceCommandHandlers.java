/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.workflowinstance;

import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.HashMap;
import java.util.Map;

public final class WorkflowInstanceCommandHandlers {

  private final Map<WorkflowInstanceIntent, WorkflowInstanceCommandHandler> handlers =
      new HashMap<>();

  public WorkflowInstanceCommandHandlers() {
    handlers.put(WorkflowInstanceIntent.CANCEL, new CancelWorkflowInstanceHandler());
  }

  public void handle(final WorkflowInstanceCommandContext context) {
    final WorkflowInstanceCommandHandler handler = handlers.get(context.getCommand());
    if (handler != null) {
      handler.handle(context);
    }
  }
}
