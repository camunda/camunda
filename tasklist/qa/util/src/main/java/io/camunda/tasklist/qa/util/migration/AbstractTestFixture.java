/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util.migration;

import io.camunda.tasklist.qa.util.TestContainerUtil;
import io.camunda.tasklist.qa.util.TestContext;
import io.zeebe.containers.ZeebeContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.GenericContainer;

public abstract class AbstractTestFixture implements TestFixture {

  protected ZeebeContainer broker;
  protected GenericContainer<?> tasklistContainer;
  @Autowired private TestContainerUtil testContainerUtil;

  @Override
  public void setup(TestContext testContext) {
    startZeebeAndTasklist(testContext);
    generateData(testContext);
    stopZeebeAndTasklist(testContext);
  }

  private void startZeebeAndTasklist(TestContext testContext) {
    broker = testContainerUtil.startZeebe(getVersion(), testContext);
    tasklistContainer = testContainerUtil.startTasklist(getVersion(), testContext);
  }

  private void stopZeebeAndTasklist(TestContext testContext) {
    testContainerUtil.stopZeebeAndTasklist(testContext);
  }

  protected void generateData(TestContext testContext) {
    // by default, no new data is created
  }
}
