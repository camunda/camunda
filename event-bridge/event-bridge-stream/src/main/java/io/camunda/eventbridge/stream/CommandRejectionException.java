/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.stream;

import io.camunda.zeebe.protocol.record.RejectionType;

/**
 * Carries a command rejection back to the waiting caller: when a processor rejects a command via
 * {@link ResponseWriter#writeRejection}, the {@link BridgingCommandResponseWriter} completes the
 * request future <em>exceptionally</em> with this — a rejected command is not a success, so it must
 * not look like one. The {@link RejectionType} mirrors the engine's rejection classification; the
 * message is the human-readable reason.
 */
public final class CommandRejectionException extends RuntimeException {

  private final transient RejectionType type;

  public CommandRejectionException(final RejectionType type, final String reason) {
    super(reason);
    this.type = type;
  }

  public RejectionType type() {
    return type;
  }
}
