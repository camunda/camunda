/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
