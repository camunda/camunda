/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util.migration;

import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.qa.util.TestContext;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;

public abstract class AbstractTestFixture implements TestFixture {

  protected TestStandaloneBroker broker;
  protected GenericContainer<?> operateContainer;
  protected TestContext testContext;
  @Autowired private TestContainerUtil testContainerUtil;

  @Override
  public void setup(final TestContext testContext) {
    this.testContext = testContext;
  }

  protected void startZeebeAndOperate() {
    broker = testContainerUtil.startZeebe(testContext);
    operateContainer = testContainerUtil.startOperate(getVersion(), testContext);
  }

  protected void stopZeebeAndOperate(final TestContext testContext) {
    testContainerUtil.stopZeebeAndOperate(testContext);
  }
}
