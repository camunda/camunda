/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.camunda.operate.data.DataGenerator;
import org.camunda.operate.es.ElasticsearchConnector;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.webapp.security.es.ElasticSearchUserDetailsService;
import org.camunda.operate.webapp.zeebe.operation.OperationExecutor;
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

  @Autowired
  private OperateProperties operateProperties;

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
