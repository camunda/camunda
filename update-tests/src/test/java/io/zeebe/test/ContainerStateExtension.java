/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import io.zeebe.test.util.testcontainers.ManagedVolume;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerStateExtension
    implements BeforeTestExecutionCallback, AfterTestExecutionCallback, TestWatcher {
  private static final Logger LOG = LoggerFactory.getLogger(ContainerStateExtension.class);

  private final ContainerState state;

  public ContainerStateExtension(final ContainerState state) {
    this.state = state;
  }

  @Override
  public void testFailed(final ExtensionContext context, final Throwable cause) {
    state.onFailure();
  }

  @Override
  public void afterTestExecution(final ExtensionContext context) {
    try {
      state.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close container state", e);
    }
  }

  @Override
  public void beforeTestExecution(final ExtensionContext context) {
    final ManagedVolume volume = ManagedVolume.newVolume();
    state.withVolume(volume);
  }
}
