/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Helper class to initiate the shutdown of the broker application. This is used to trigger graceful
 * shutdown the broker from within the application itself.
 */
@Component
public class BrokerShutdownHelper {

  @Autowired private ApplicationContext appContext;

  public void initiateShutdown(final int returnCode) {
    // This can be called from an Actor. We should ensure that any blocking operation is not run on
    // the actor. Hence, schedule it on a common thread pool.
    Thread.ofVirtual().start(() -> shutdown(returnCode));
  }

  private void shutdown(final int returnCode) {
    SpringApplication.exit(appContext, () -> returnCode);
  }
}
