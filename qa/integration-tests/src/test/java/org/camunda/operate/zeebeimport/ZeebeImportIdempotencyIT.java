/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import java.util.function.Predicate;

import org.camunda.operate.TestApplication;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.apps.idempotency.ZeebeImportIdempotencyTestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tests that even if the Zeebe data is imported twice, in Operate Elasticsearch is is still consistent.
 */
@SpringBootTest(
  classes = {ZeebeImportIdempotencyTestConfig.class, TestApplication.class},
  properties = {OperateProperties.PREFIX + ".importProperties.startLoadingDataOnStartup = false", "spring.main.allow-bean-definition-overriding=true"})
public class ZeebeImportIdempotencyIT extends ZeebeImportIT {

  @Autowired
  private ZeebeImportIdempotencyTestConfig.CustomElasticsearchBulkProcessor elasticsearchBulkProcessor;
  
  @Override
  protected int processImportTypeAndWait(ImportValueType importValueType, Predicate<Object[]> waitTill, Object... arguments) {
    int processed = elasticsearchTestRule.processImportTypeAndWait(importValueType, waitTill, arguments);
    processed += elasticsearchTestRule.processImportTypeAndWait(importValueType, waitTill, arguments);
    elasticsearchBulkProcessor.cancelAttempts();
    return processed;
  }

}