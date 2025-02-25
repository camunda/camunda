/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch.client;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.TestUtil;
import java.util.function.Function;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public abstract class AbstractOpenSearchOperationIT extends OperateAbstractIT {
  @Autowired protected RichOpenSearchClient richOpenSearchClient;

  @Autowired protected SchemaManager schemaManager;

  @Autowired protected OperateProperties operateProperties;

  @Autowired protected OpensearchTestDataHelper opensearchTestDataHelper;

  protected String indexPrefix;

  @Before
  public void setUp() {
    indexPrefix = "test-opensearch-operation-" + TestUtil.createRandomString(5);
    operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
    schemaManager.createSchema();
  }

  @After
  public void cleanUp() {
    schemaManager.deleteIndicesFor(indexPrefix + "*");
  }

  public <R> R withThreadPoolTaskScheduler(Function<ThreadPoolTaskScheduler, R> f) {
    final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(5);
    scheduler.setThreadNamePrefix(this.getClass().getSimpleName());
    scheduler.initialize();

    try {
      return f.apply(scheduler);
    } finally {
      scheduler.shutdown();
    }
  }
}
