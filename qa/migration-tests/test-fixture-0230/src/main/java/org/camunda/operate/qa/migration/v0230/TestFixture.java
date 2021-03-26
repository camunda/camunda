/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.migration.v0230;

import org.camunda.operate.qa.util.migration.AbstractTestFixture;
import org.camunda.operate.qa.util.migration.TestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestFixture extends AbstractTestFixture {

  public static final String VERSION = "0.23.0";

  @Autowired
  private Process0230DataGenerator basicProcessDataGenerator;

  @Override
  public void setup(TestContext testContext) {
    super.setup(testContext);
    startZeebeAndOperate();
    generateData();
    stopZeebeAndOperate();
  }

  @Override
  public String getVersion() {
    return VERSION;
  }

  private void generateData() {
    try {
      basicProcessDataGenerator.createData(testContext);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
