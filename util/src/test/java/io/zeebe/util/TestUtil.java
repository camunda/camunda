/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BooleanSupplier;

public final class TestUtil {

  public static void waitUntil(final BooleanSupplier condition) {
    boolean isMet = false;
    int iterations = 0;

    while (!isMet && iterations < 100) {
      isMet = condition.getAsBoolean();

      if (!isMet) {
        iterations += 1;

        try {
          Thread.sleep(10);
        } catch (final InterruptedException e) {
          e.printStackTrace();
        }
      }
    }

    assertThat(isMet).isTrue();
  }
}
