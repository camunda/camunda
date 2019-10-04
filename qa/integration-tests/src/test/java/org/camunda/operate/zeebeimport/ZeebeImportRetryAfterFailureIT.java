/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import org.camunda.operate.TestApplication;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.apps.retry_after_failure.RetryAfterFailureTestConfig;
import org.junit.After;
import org.junit.Ignore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tests that after one failure of specific batch import, it will be successfully retried.
 */
@Ignore("OPE-579")
@SpringBootTest(
  classes = {RetryAfterFailureTestConfig.class, TestApplication.class},
  properties = {OperateProperties.PREFIX + ".importProperties.startLoadingDataOnStartup = false", "spring.main.allow-bean-definition-overriding=true"})
public class ZeebeImportRetryAfterFailureIT extends ZeebeImportIT {

  @Autowired
  private RetryAfterFailureTestConfig.CustomElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @After
  public void after() {
    elasticsearchBulkProcessor.cancelAttempts();
    super.after();
  }

}