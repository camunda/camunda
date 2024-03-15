/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.event;

import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.context.ApplicationEvent;

/**
 * Emitted when the ZeebeClient is about to close. Typically, during application shutdown, but maybe
 * more often in test case or never if the ZeebeClient is disabled, see {@link
 * ZeebeClientCreatedEvent} for more details
 */
public class ZeebeClientClosingEvent extends ApplicationEvent {

  public final ZeebeClient client;

  public ZeebeClientClosingEvent(final Object source, final ZeebeClient client) {
    super(source);
    this.client = client;
  }

  public ZeebeClient getClient() {
    return client;
  }
}
