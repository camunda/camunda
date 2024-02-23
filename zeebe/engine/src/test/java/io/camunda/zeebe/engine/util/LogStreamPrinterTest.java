/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import org.junit.Rule;
import org.junit.Test;

public final class LogStreamPrinterTest {

  @Rule public final StreamProcessorRule rule = new StreamProcessorRule();

  /**
   * This tests just assures that we do not remove this utility as unused code, as it's only purpose
   * is to be used temporarily for debugging
   */
  @Test
  public void testExistanceOfClass() {
    rule.printAllRecords();
  }
}
