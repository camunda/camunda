/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util.migration;

import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.qa.util.TestContext;
import io.zeebe.containers.ZeebeContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;

public abstract class AbstractTestFixture implements TestFixture {

  @Autowired
  private TestContainerUtil testContainerUtil;
  protected ZeebeContainer broker;
  protected GenericContainer<?> operateContainer;

  protected TestContext testContext;

  @Override
  public void setup(TestContext testContext) {
    this.testContext = testContext;
  }

  protected void startZeebeAndOperate() {
    broker = testContainerUtil.startZeebe(getVersion(), testContext);
    operateContainer = testContainerUtil.startOperate(getVersion(), testContext);
  }

  protected void stopZeebeAndOperate(TestContext testContext) {
    testContainerUtil.stopZeebeAndOperate(testContext);
  }

}
