/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate;

import java.util.Arrays;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.camunda.operate.data.DataGenerator;
import org.camunda.operate.es.ElasticsearchConnector;
import org.camunda.operate.es.ElasticsearchSchemaManager;
import org.camunda.operate.es.archiver.Archiver;
import org.camunda.operate.user.ElasticSearchUserDetailsService;
import org.camunda.operate.zeebe.operation.OperationExecutor;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StartupBean {

  private static final Logger logger = LoggerFactory.getLogger(StartupBean.class);

  @Autowired
  private RestHighLevelClient esClient;
  
  @Autowired
  private RestHighLevelClient zeebeEsClient; 
  
  @Autowired
  private ElasticsearchSchemaManager elasticsearchSchemaManager;
  
  @Autowired
  private ElasticSearchUserDetailsService elasticsearchUserDetailsService;

  @Autowired
  private DataGenerator dataGenerator;

  @Autowired
  private ZeebeImporter zeebeImporter;

  @Autowired
  private OperationExecutor operationExecutor;

  @Autowired
  private Archiver archiver;

  @PostConstruct
  public void initApplication() {
    logger.info("INIT: Initialize Elasticsearch schema...");
    elasticsearchSchemaManager.initializeSchema();
    logger.info("INIT: Create users in elasticsearch if not exists ...");
    elasticsearchUserDetailsService.initializeUsers();
    logger.debug("INIT: Generate demo data...");
    try {
      dataGenerator.createZeebeDataAsync(false);
    } catch (Exception ex) {
      logger.debug("Demo data could not be generated. Cause: {}", ex.getMessage());
      logger.error("Error occurred when generating demo data.", ex);
    }
    logger.info("INIT: Start importing Zeebe data...");
    zeebeImporter.startImportingData();
    logger.info("INIT: Start operation executor...");
    operationExecutor.startExecuting();
    logger.info("INIT: Start archiving data...");
    archiver.startArchiving();
    logger.info("INIT: DONE");
  }
  
  @PreDestroy
  public void shutdown() {
    logger.info("Shutdown Operate application");
    for(Shutdownable shutdownable: Arrays.asList(archiver,operationExecutor,zeebeImporter,dataGenerator)) {
      if(shutdownable!=null) {
        shutdownable.shutdown();
      }
    }
    ElasticsearchConnector.closeEsClient(esClient);
    ElasticsearchConnector.closeEsClient(zeebeEsClient);
  }
  
}
