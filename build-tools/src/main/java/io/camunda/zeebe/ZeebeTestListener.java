/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe;

import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZeebeTestListener extends RunListener {

  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.test");

  @Override
  public void testStarted(final Description description) throws Exception {
    LOG.info("Test started: {}", description.getDisplayName());
  }

  @Override
  public void testFinished(final Description description) throws Exception {
    LOG.info("Test finished: {}", description.getDisplayName());
  }
}
