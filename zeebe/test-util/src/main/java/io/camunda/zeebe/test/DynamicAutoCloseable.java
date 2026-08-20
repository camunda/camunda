/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that can managed AutoCloseable added dynamically. AutoCloseable are closed in the opposite
 * order they were added.
 */
public class DynamicAutoCloseable implements AutoCloseable {

  final List<AutoCloseable> thingsToClose = new ArrayList<>();

  public <A extends AutoCloseable> A manage(final A closeable) {
    thingsToClose.add(closeable);
    return closeable;
  }

  @Override
  public void close() {
    RuntimeException firstException = null;
    final int size = thingsToClose.size();
    for (int i = size - 1; i >= 0; i--) {
      try {
        thingsToClose.remove(i).close();
      } catch (final Exception e) {
        if (firstException == null) {
          firstException = new RuntimeException(e);
        }
      }
    }
    if (firstException != null) {
      throw firstException;
    }
  }
}
