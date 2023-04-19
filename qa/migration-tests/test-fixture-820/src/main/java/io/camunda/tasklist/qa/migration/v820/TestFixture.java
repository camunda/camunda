/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.migration.v820;

import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.migration.AbstractTestFixture;
import org.springframework.stereotype.Component;

@Component
public class TestFixture extends AbstractTestFixture {

  public static final String VERSION = "8.2.0";

  @Override
  public void setup(TestContext testContext) {
    super.setup(testContext);
    startZeebeAndTasklist();
    // no new data is created
    stopZeebeAndTasklist(testContext);
  }

  @Override
  public String getVersion() {
    return VERSION;
  }
}
