/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestEnvironment {
  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.test.util");

  private static final String TEST_FORK_NUMBER_PROPERTY_NAME = "testForkNumber";
  private static final String TEST_MAVEN_ID_PROPERTY_NAME = "testMavenId";

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

  /**
   * Returns the test maven ID that maps to a test stage in Jenkins (e.g. junit = 1; it = 2; junit8
   * = 3
   *
   * @return test maven ID that maps to a test stage in Jenkins (e.g. junit = 1; it = 2; junit8 = 3
   */
  public static int getTestMavenId() {
    int testMavenId = 0;
    try {
      final String testMavenIdProperty = System.getProperty(TEST_MAVEN_ID_PROPERTY_NAME);
      if (testMavenIdProperty != null) {
        testMavenId = Integer.parseInt(testMavenIdProperty);
      } else {
        LOG.warn(
            "No system property '{}' set, using default value {}",
            TEST_MAVEN_ID_PROPERTY_NAME,
            testMavenId);
      }
    } catch (final Exception e) {
      LOG.warn("Failed to read test maven id system property", e);
    }
    return testMavenId;
  }
}
