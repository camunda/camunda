/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import java.util.function.Supplier;

/**
 * The writers a command processor uses, mirroring the engine's {@code Writers}. All are built once
 * against the same result-builder supplier and handed to processors at construction:
 *
 * <ul>
 *   <li>{@link #state()} — append a follow-up event and apply it (the single state-mutation path).
 *   <li>{@link #command()} — append a follow-up command (processed next, e.g. a debounced
 *       rebalance).
 *   <li>{@link #response()} — stage the reply to a request-command (flushed after commit),
 *       including a rejection reply.
 *   <li>{@link #rejection()} — append a {@code COMMAND_REJECTION} record to the log (observability
 *       / deterministic stream); pair with {@code response().writeRejection} to also fail the
 *       caller.
 * </ul>
 */
public final class Writers {

  private final StateWriter state;
  private final CommandWriter command;
  private final ResponseWriter response;
  private final RejectionWriter rejection;

  Writers(final Supplier<ProcessingResultBuilder> resultBuilder, final EventApplier eventApplier) {
    state = new StateWriter(resultBuilder, eventApplier);
    command = new CommandWriter(resultBuilder);
    response = new ResponseWriter(resultBuilder);
    rejection = new RejectionWriter(resultBuilder);
  }

  public StateWriter state() {
    return state;
  }

  public CommandWriter command() {
    return command;
  }

  public ResponseWriter response() {
    return response;
  }

  public RejectionWriter rejection() {
    return rejection;
  }
}
