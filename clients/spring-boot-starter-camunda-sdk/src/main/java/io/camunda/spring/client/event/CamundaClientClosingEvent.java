/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.event;

import io.camunda.client.CamundaClient;
import org.springframework.context.ApplicationEvent;

/**
 * Emitted when the CamundaClient is about to close. Typically, during application shutdown, but
 * maybe more often in test case or never if the CamundaClient is disabled, see {@link
 * CamundaClientCreatedEvent} for more details
 */
public class CamundaClientClosingEvent extends ApplicationEvent {

  public final CamundaClient client;

  public CamundaClientClosingEvent(final Object source, final CamundaClient client) {
    super(source);
    this.client = client;
  }

  public CamundaClient getClient() {
    return client;
  }
}
