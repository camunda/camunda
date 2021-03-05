/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.util;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.rules.ExternalResource;

/**
 * Saves you some {@link After} methods by closing {@link AutoCloseable} implementations after the
 * test in LIFO fashion.
 *
 * @author Lindhauer
 */
public final class AutoCloseableRule extends ExternalResource {

  final List<AutoCloseable> thingsToClose = new ArrayList<>();

  public void manage(final AutoCloseable closeable) {
    thingsToClose.add(closeable);
  }

  @Override
  public void after() {
    final int size = thingsToClose.size();
    for (int i = size - 1; i >= 0; i--) {
      try {
        thingsToClose.remove(i).close();
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
