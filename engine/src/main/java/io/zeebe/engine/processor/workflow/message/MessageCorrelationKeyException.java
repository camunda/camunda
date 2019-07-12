/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.message;

public class MessageCorrelationKeyException extends RuntimeException {
  private static final long serialVersionUID = 8929284049646192937L;

  private final MessageCorrelationKeyContext context;

  public MessageCorrelationKeyException(MessageCorrelationKeyContext context, String message) {
    super(message);
    this.context = context;
  }

  public MessageCorrelationKeyContext getContext() {
    return context;
  }
}
