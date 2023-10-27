/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.migration.v800;

import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.migration.AbstractTestFixture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestFixture extends AbstractTestFixture {

  public static final String VERSION = "8.0.0";

  @Autowired private BasicProcessDataGenerator basicProcessDataGenerator;

  @Autowired private BigVariableProcessDataGenerator bigVariableProcessDataGenerator;

  @Override
  public String getVersion() {
    return VERSION;
  }

  @Override
  protected void generateData(TestContext testContext) {
    try {
      basicProcessDataGenerator.createData(testContext);
      bigVariableProcessDataGenerator.createData(testContext);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
