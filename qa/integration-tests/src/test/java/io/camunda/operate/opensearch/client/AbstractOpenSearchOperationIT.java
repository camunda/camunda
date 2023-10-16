/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.opensearch.client;

import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.SchemaManager;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.operate.util.OpensearchOperateAbstractIT;
import io.camunda.operate.util.TestUtil;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.function.Function;

public abstract class AbstractOpenSearchOperationIT extends OpensearchOperateAbstractIT {
  @Autowired
  protected RichOpenSearchClient richOpenSearchClient;

  @Autowired
  protected SchemaManager schemaManager;

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  protected OpensearchTestDataHelper opensearchTestDataHelper;

  protected String indexPrefix;

  @Before
  public void setUp(){
    indexPrefix = "test-opensearch-operation-"+ TestUtil.createRandomString(5);
    operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
    schemaManager.createSchema();
  }

  @After
  public void cleanUp() {
    schemaManager.deleteIndicesFor(indexPrefix +"*");
  }

  public  <R> R withThreadPoolTaskScheduler(Function<ThreadPoolTaskScheduler, R> f){
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(5);
    scheduler.setThreadNamePrefix(this.getClass().getSimpleName());
    scheduler.initialize();

    try{
      return f.apply(scheduler);
    } finally {
      scheduler.shutdown();
    }
  }

}
