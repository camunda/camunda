/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.listeners;

import io.camunda.application.commons.initializer.DataInitializer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

public class ApplicationReadyListener implements ApplicationListener<ApplicationReadyEvent> {

  public ApplicationReadyListener() {}

  @Override
  public void onApplicationEvent(final ApplicationReadyEvent event) {
    event.getApplicationContext().getBean(DataInitializer.class).initialize();
  }
}
