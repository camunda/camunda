/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.util.TestApplication;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.apps.retry_after_failure.RetryAfterFailureTestConfig;
import org.junit.After;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tests that after one failure of specific batch import, it will be successfully retried.
 */
@SpringBootTest(
  classes = {RetryAfterFailureTestConfig.class, TestApplication.class},
  properties = {OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.main.allow-bean-definition-overriding=true"})
public class ZeebeImportRetryAfterFailureIT extends ZeebeImportIT {

  @Autowired
  private RetryAfterFailureTestConfig.CustomElasticsearchBulkProcessor elasticsearchBulkProcessor;

  @After
  public void after() {
    elasticsearchBulkProcessor.cancelAttempts();
    super.after();
  }

}