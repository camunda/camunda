/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ApplicationShutdownService {
  private static final int SYSTEM_ERROR = 1;

  @Autowired private ConfigurableApplicationContext context;

  public void shutdown() {
    final int exitCode = SpringApplication.exit(context, () -> SYSTEM_ERROR);
    System.exit(exitCode);
  }
}
