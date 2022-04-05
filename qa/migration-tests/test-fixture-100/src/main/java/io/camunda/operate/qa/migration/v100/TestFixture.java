/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration.v100;

import io.camunda.operate.qa.util.migration.AbstractTestFixture;
import io.camunda.operate.qa.util.migration.TestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestFixture extends AbstractTestFixture {

  public static final String VERSION = "1.0.0";

  @Autowired
  private BasicProcessDataGenerator basicProcessDataGenerator;

  @Autowired
  private BigProcessDataGenerator bigProcessDataGenerator;

  @Autowired
  private BigVariableDataGenerator bigVariableDataGenerator;

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
      bigProcessDataGenerator.createData(testContext);
      bigVariableDataGenerator.createData(testContext);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

}
