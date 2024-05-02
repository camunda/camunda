/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.apps.idempotency.ZeebeImportIdempotencyTestConfig;
import io.camunda.operate.zeebe.ImportValueType;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Tests that even if the Zeebe data is imported twice, in Operate Elasticsearch is is still
 * consistent.
 */
@SpringBootTest(
    classes = {ZeebeImportIdempotencyTestConfig.class, TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.main.allow-bean-definition-overriding=true",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    })
public class IdempotencyZeebeImportIT extends BasicZeebeImportIT {

  @Autowired
  private ZeebeImportIdempotencyTestConfig.CustomElasticsearchBulkProcessor
      elasticsearchBulkProcessor;

  @Override
  protected void processImportTypeAndWait(
      final ImportValueType importValueType,
      final Predicate<Object[]> waitTill,
      final Object... arguments) {
    searchTestRule.processRecordsWithTypeAndWait(importValueType, waitTill, arguments);
    searchTestRule.processRecordsWithTypeAndWait(importValueType, waitTill, arguments);
    elasticsearchBulkProcessor.cancelAttempts();
  }
}
