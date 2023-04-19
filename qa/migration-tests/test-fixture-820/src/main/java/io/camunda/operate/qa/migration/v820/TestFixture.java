/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration.v820;

import io.camunda.operate.qa.util.migration.AbstractTestFixture;
import io.camunda.operate.qa.util.TestContext;
import org.springframework.stereotype.Component;

@Component
public class TestFixture extends AbstractTestFixture {

  public static final String VERSION = "8.2.0";

  @Override
  public void setup(TestContext testContext) {
    super.setup(testContext);
    startZeebeAndOperate();
    //no additional data is needed
    stopZeebeAndOperate(testContext);
  }

  @Override
  public String getVersion() {
    return VERSION;
  }

}
