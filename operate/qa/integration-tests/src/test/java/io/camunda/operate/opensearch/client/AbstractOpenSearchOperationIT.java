/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch.client;

import io.camunda.operate.conditions.DatabaseCondition;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.schema.util.camunda.exporter.SchemaWithExporter;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.OpensearchOperateAbstractIT;
import io.camunda.operate.util.TestUtil;
import java.util.function.Function;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = DatabaseCondition.DATABASE_PROPERTY + "=opensearch")
public abstract class AbstractOpenSearchOperationIT extends OpensearchOperateAbstractIT {
  @Autowired protected RichOpenSearchClient richOpenSearchClient;
  @Autowired protected SchemaManager schemaManager;
  @Autowired protected OperateProperties operateProperties;
  @Autowired protected OpensearchTestDataHelper opensearchTestDataHelper;
  protected String indexPrefix;

  @BeforeClass
  public static void beforeClass() {}

  @Before
  public void setUp() {
    final var indexPrefix = "test-opensearch-operation-" + TestUtil.createRandomString(5);
    operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
    final var schemaExporterHelper = new SchemaWithExporter(indexPrefix, false);
    schemaExporterHelper.createSchema();
  }

  @After
  public void cleanUp() {
    TestUtil.removeAllIndices(richOpenSearchClient.index(), richOpenSearchClient.template(), "*");
  }

  public <R> R withThreadPoolTaskScheduler(final Function<ThreadPoolTaskScheduler, R> f) {
    final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(5);
    scheduler.setThreadNamePrefix(getClass().getSimpleName());
    scheduler.initialize();

    try {
      return f.apply(scheduler);
    } finally {
      scheduler.shutdown();
    }
  }
}
