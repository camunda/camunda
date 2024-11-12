/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.util.TestCheck;
import io.camunda.tasklist.util.apps.idempotency.ZeebeImportIdempotencyOpenSearchTestConfig;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tests that even if the Zeebe data is imported twice, in Tasklist OpenSearch is is still
 * consistent.
 */
@SpringBootTest(
    classes = {ZeebeImportIdempotencyOpenSearchTestConfig.class, TestApplication.class},
    properties = {
      TasklistProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      TasklistProperties.PREFIX + ".archiver.rolloverEnabled = false",
      TasklistProperties.PREFIX + "importer.jobType = testJobType",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ZeebeImportIdempotencyOpenSearchIT extends ZeebeImportIT {

  @Autowired
  private ZeebeImportIdempotencyOpenSearchTestConfig.CustomOpenSearchBulkProcessor
      customOpenSearchBulkProcessor;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isOpenSearch());
  }

  @Override
  protected void processAllRecordsAndWait(final TestCheck waitTill, final Object... arguments) {
    databaseTestExtension.processAllRecordsAndWait(waitTill, arguments);
    databaseTestExtension.processAllRecordsAndWait(waitTill, arguments);
    customOpenSearchBulkProcessor.cancelAttempts();
  }
}
