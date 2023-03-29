/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import io.camunda.operate.data.DataGenerator;
import io.camunda.operate.es.ElasticsearchConnector;
import io.camunda.operate.webapp.security.es.ElasticSearchUserDetailsService;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaStartup")
@Profile("!test")
public class StartupBean {

  private static final Logger logger = LoggerFactory.getLogger(StartupBean.class);

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private RestHighLevelClient zeebeEsClient;

  @Autowired(required = false)
  private ElasticSearchUserDetailsService elasticsearchUserDetailsService;

  @Autowired
  private DataGenerator dataGenerator;

  @Autowired
  private OperationExecutor operationExecutor;
  @PostConstruct
  public void initApplication() {
    if (elasticsearchUserDetailsService != null) {
      logger.info("INIT: Create users in elasticsearch if not exists ...");
      elasticsearchUserDetailsService.initializeUsers();
    }
    logger.debug("INIT: Generate demo data...");
    try {
      dataGenerator.createZeebeDataAsync(false);
    } catch (Exception ex) {
      logger.debug("Demo data could not be generated. Cause: {}", ex.getMessage());
      logger.error("Error occurred when generating demo data.", ex);
    }
    logger.info("INIT: Start operation executor...");
    operationExecutor.startExecuting();
    logger.info("INIT: DONE");
  }

  @PreDestroy
  public void shutdown() {
    logger.info("Shutdown elasticsearch clients.");
    ElasticsearchConnector.closeEsClient(esClient);
    ElasticsearchConnector.closeEsClient(zeebeEsClient);
  }

}
