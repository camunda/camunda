/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.event;

import io.camunda.spring.client.annotation.processor.CamundaAnnotationProcessorRegistry;
import org.springframework.context.event.EventListener;

public class CamundaClientEventListener {

  private final CamundaAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry;

  public CamundaClientEventListener(
      final CamundaAnnotationProcessorRegistry zeebeAnnotationProcessorRegistry) {
    this.zeebeAnnotationProcessorRegistry = zeebeAnnotationProcessorRegistry;
  }

  @EventListener
  public void handleStart(final CamundaClientCreatedEvent evt) {
    zeebeAnnotationProcessorRegistry.startAll(evt.getClient());
  }

  @EventListener
  public void handleStop(final CamundaClientClosingEvent evt) {
    zeebeAnnotationProcessorRegistry.stopAll(evt.getClient());
  }
}
