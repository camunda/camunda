/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.data;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.camunda.operate.property.OperateProperties;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.beans.factory.annotation.Qualifier;
import static io.camunda.operate.util.ThreadUtil.sleepFor;

public abstract class AbstractDataGenerator implements DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(AbstractDataGenerator.class);

  private boolean shutdown = false;

  @Autowired
  protected ZeebeClient client;

  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired
  private OperateProperties operateProperties;

  protected boolean manuallyCalled = false;

  protected ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

  @PreDestroy
  public void shutdown() {
    logger.info("Shutdown DataGenerator");
    shutdown = true;
    if(scheduler!=null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(200, TimeUnit.MILLISECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
      }
    }
  }

  @Override
  public void createZeebeDataAsync(boolean manuallyCalled) {
    scheduler.execute(() -> {
      Boolean zeebeDataCreated = null;
      while (zeebeDataCreated == null && !shutdown) {
        try {
          zeebeDataCreated = createZeebeData(manuallyCalled);
        } catch (Exception ex) {
          logger.error(String.format("Error occurred when creating demo data: %s. Retrying...", ex.getMessage()), ex);
          sleepFor(2000);
        }
      }
    });
  }

  public boolean createZeebeData(boolean manuallyCalled) {
    this.manuallyCalled = manuallyCalled;

    if (!shouldCreateData(manuallyCalled)) {
      return false;
    }

    return true;
  }

  public boolean shouldCreateData(boolean manuallyCalled) {
    if (!manuallyCalled) {    //when called manually, always create the data
      try {
        GetIndexRequest request = new GetIndexRequest(operateProperties.getZeebeElasticsearch().getPrefix() + "*");
        request.indicesOptions(IndicesOptions.fromOptions(true, false, true, false));
        boolean exists = zeebeEsClient.indices().exists(request, RequestOptions.DEFAULT);
        if (exists) {
          //data already exists
          logger.debug("Data already exists in Zeebe.");
          return false;
        }
      } catch (IOException io) {
        logger.debug("Error occurred while checking existance of data in Zeebe: {}. Demo data won't be created.", io.getMessage());
        return false;
      }
    }
    return true;
  }


}
