/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

import io.camunda.zeebe.el.Expression;

/**
 * The properties of an element that publishes a message. For example, a throw message event,
 * message end event or send task.
 */
public class PublishMessageProperties {
  private Expression messageName;
  private Expression messageId;
  private Expression correlationKey;
  private Expression timeToLive;

  public Expression getMessageName() {
    return messageName;
  }

  public void setMessageName(final Expression messageName) {
    this.messageName = messageName;
  }

  public Expression getMessageId() {
    return messageId;
  }

  public void setMessageId(final Expression messageId) {
    this.messageId = messageId;
  }

  public Expression getCorrelationKey() {
    return correlationKey;
  }

  public void setCorrelationKey(final Expression correlationKey) {
    this.correlationKey = correlationKey;
  }

  public Expression getTimeToLive() {
    return timeToLive;
  }

  public void setTimeToLive(final Expression timeToLive) {
    this.timeToLive = timeToLive;
  }
}
