/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util;

import io.camunda.zeebe.test.DynamicAutoCloseable;
import org.junit.After;
import org.junit.rules.ExternalResource;

/**
 * Saves you some {@link After} methods by closing {@link AutoCloseable} implementations after the
 * test in LIFO fashion.
 *
 * @author Lindhauer
 */
public final class AutoCloseableRule extends ExternalResource {

  private final DynamicAutoCloseable dynAutoCloseable = new DynamicAutoCloseable();

  public <A extends AutoCloseable> A manage(final A closeable) {
    dynAutoCloseable.manage(closeable);
    return closeable;
  }

  @Override
  public void after() {
    dynAutoCloseable.close();
  }
}
