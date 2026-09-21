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

  /**
   * Name of the system property that holds the name of the property carrying the Gradle test-worker
   * ID. The Gradle test task sets this pointer so test code can read the worker ID without
   * hard-coding Gradle's internal worker-ID property name.
   */
  private static final String GRADLE_WORKER_ID_PROPERTY_NAME = "test.gradleWorkerIdProperty";

  private TestEnvironment() {}

  /** Returns whether the current JVM runs inside a Gradle test worker. */
  public static boolean isGradleWorker() {
    return System.getProperty(GRADLE_WORKER_ID_PROPERTY_NAME) != null;
  }

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
      } else if (isGradleWorker()) {
        // Gradle doesn't expose a per-worker fork number directly. Preserve its worker ID for
        // diagnostics; SocketUtil uses OS-assigned ports for Gradle workers instead of treating
        // the global ID as a bounded port-range slot.
        final String workerId = gradleWorkerId();
        if (workerId != null) {
          testForkNumber = Integer.parseInt(workerId) - 1;
        }
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

  private static String gradleWorkerId() {
    final String propertyName = System.getProperty(GRADLE_WORKER_ID_PROPERTY_NAME);
    return propertyName == null ? null : System.getProperty(propertyName);
  }
}
