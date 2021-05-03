/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.message;

public final class MessageCorrelationKeyException extends RuntimeException {
  private static final long serialVersionUID = 8929284049646192937L;

  private final long variableScopeKey;

  public MessageCorrelationKeyException(final long variableScopeKey, final String message) {
    super(message);
    this.variableScopeKey = variableScopeKey;
  }

  public long getVariableScopeKey() {
    return variableScopeKey;
  }
}
