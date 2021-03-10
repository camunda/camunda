/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.processinstance;

import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.HashMap;
import java.util.Map;

public final class ProcessInstanceCommandHandlers {

  private final Map<ProcessInstanceIntent, ProcessInstanceCommandHandler> handlers =
      new HashMap<>();

  public ProcessInstanceCommandHandlers() {
    handlers.put(ProcessInstanceIntent.CANCEL, new CancelProcessInstanceHandler());
  }

  public void handle(final ProcessInstanceCommandContext context) {
    final ProcessInstanceCommandHandler handler = handlers.get(context.getCommand());
    if (handler != null) {
      handler.handle(context);
    }
  }
}
