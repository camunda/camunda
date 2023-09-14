/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.util.TestUtil;
import io.camunda.tasklist.util.apps.retry_after_failure.RetryAfterFailureTestOpenSearchConfig;
import org.junit.After;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Tests that after one failure of specific batch import, it will be successfully retried. */
@SpringBootTest(
    classes = {RetryAfterFailureTestOpenSearchConfig.class, TestApplication.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.main.allow-bean-definition-overriding=true",
      TasklistProperties.PREFIX + "importer.jobType = testJobType",
      "graphql.servlet.exception-handlers-enabled = true"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ZeebeImportRetryAfterFailureOpenSearchIT extends ZeebeImportIT {

  @Autowired
  private RetryAfterFailureTestOpenSearchConfig.CustomOpenSearchBulkProcessor
      openSearchBulkProcessor;

  @BeforeClass
  public static void beforeClass() {
    Assume.assumeTrue(TestUtil.isOpenSearch());
  }

  @After
  public void after() {
    openSearchBulkProcessor.cancelAttempts();
    super.after();
  }
}
