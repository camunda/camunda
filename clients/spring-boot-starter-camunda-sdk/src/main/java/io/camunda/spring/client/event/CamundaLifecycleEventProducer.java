/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.event;

import io.camunda.client.CamundaClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;

public class CamundaLifecycleEventProducer implements SmartLifecycle {

  protected boolean running = false;

  private final ApplicationEventPublisher publisher;

  private final CamundaClient client;

  public CamundaLifecycleEventProducer(
      final CamundaClient client, final ApplicationEventPublisher publisher) {
    this.client = client;
    this.publisher = publisher;
  }

  @Override
  public void start() {
    publisher.publishEvent(new CamundaClientCreatedEvent(this, client));
    running = true;
  }

  @Override
  public void stop() {
    publisher.publishEvent(new CamundaClientClosingEvent(this, client));
    running = false;
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
