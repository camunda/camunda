/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestEnvironment {
  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.test.util");

  private static final String TEST_FORK_NUMBER_PROPERTY_NAME = "testForkNumber";

  private TestEnvironment() {}

  /**
   * Returns the test fork number
   *
   * @return test fork number
   */
  public static int getTestForkNumber() {
    int testForkNumber = 0;
    try {
      final String testForkNumberProperty = System.getProperty(TEST_FORK_NUMBER_PROPERTY_NAME);
      if (testForkNumberProperty != null) {
        testForkNumber = Integer.parseInt(testForkNumberProperty);
      } else {
        LOG.warn(
            "No system property '{}' set, using default value {}",
            TEST_FORK_NUMBER_PROPERTY_NAME,
            testForkNumber);
      }
    } catch (final Exception e) {
      LOG.warn("Failed to read test fork number system property", e);
    }
    return testForkNumber;
  }
}
