/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import java.util.function.Predicate;

import io.camunda.operate.util.TestApplication;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.apps.idempotency.ZeebeImportIdempotencyTestConfig;
import io.camunda.operate.zeebe.ImportValueType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tests that even if the Zeebe data is imported twice, in Operate Elasticsearch is is still consistent.
 */
@SpringBootTest(
  classes = {ZeebeImportIdempotencyTestConfig.class, TestApplication.class},

  properties = {OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.main.allow-bean-definition-overriding=true"})
public class ZeebeImportIdempotencyIT extends ZeebeImportIT {

  @Autowired
  private ZeebeImportIdempotencyTestConfig.CustomElasticsearchBulkProcessor elasticsearchBulkProcessor;
  
  @Override
  protected void processImportTypeAndWait(ImportValueType importValueType, Predicate<Object[]> waitTill, Object... arguments) {
    elasticsearchTestRule.processRecordsWithTypeAndWait(importValueType, waitTill, arguments);
    elasticsearchTestRule.processRecordsWithTypeAndWait(importValueType, waitTill, arguments);
    elasticsearchBulkProcessor.cancelAttempts();
  }

}