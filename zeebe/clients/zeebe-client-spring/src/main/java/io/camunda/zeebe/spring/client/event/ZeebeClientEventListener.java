/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.spring.client.event;

import io.camunda.zeebe.spring.client.annotation.processor.ZeebeAnnotationProcessorRegistry;
import org.springframework.context.event.EventListener;

public class ZeebeClientEventListener {

  private final ZeebeAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry;

  public ZeebeClientEventListener(
      final ZeebeAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry) {
    this.zeebeAnnotationProcessorRegistry = zeebeAnnotationProcessorRegistry;
  }

  @EventListener
  public void handleStart(final ZeebeClientCreatedEvent evt) {
    zeebeAnnotationProcessorRegistry.startAll(evt.getClient());
  }

  @EventListener
  public void handleStop(final ZeebeClientClosingEvent evt) {
    zeebeAnnotationProcessorRegistry.stopAll(evt.getClient());
  }
}
