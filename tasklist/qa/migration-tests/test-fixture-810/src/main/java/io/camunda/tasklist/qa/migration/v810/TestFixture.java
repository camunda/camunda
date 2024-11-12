/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.migration.v810;

import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.migration.AbstractTestFixture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestFixture extends AbstractTestFixture {

  public static final String VERSION = "8.1.27";

  @Autowired private BasicProcessDataGenerator basicProcessDataGenerator;

  @Autowired private BigVariableProcessDataGenerator bigVariableProcessDataGenerator;

  @Override
  public String getVersion() {
    return VERSION;
  }

  @Override
  protected void generateData(final TestContext testContext) {
    try {
      basicProcessDataGenerator.createData(testContext);
      bigVariableProcessDataGenerator.createData(testContext);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }
}
