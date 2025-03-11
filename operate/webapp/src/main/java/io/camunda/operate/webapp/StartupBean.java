/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.ElasticsearchConnector;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.webapp.security.auth.OperateUserDetailsService;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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

  private static final Logger LOGGER = LoggerFactory.getLogger(StartupBean.class);

  @Autowired(required = false)
  private RestHighLevelClient esClient;

  @Autowired(required = false)
  private RestHighLevelClient zeebeEsClient;

  @Autowired(required = false)
  private ElasticsearchClient elasticsearchClient;

  @Autowired(required = false)
  private OperateUserDetailsService operateUserDetailsService;

  @Autowired private OperateProperties operateProperties;

  @Autowired private OperationExecutor operationExecutor;

  @PostConstruct
  public void initApplication() {
    if (operateUserDetailsService != null) {
      LOGGER.info(
          "INIT: Create users in {} if not exists ...", DatabaseInfo.getCurrent().getCode());
      operateUserDetailsService.initializeUsers();
    }

    LOGGER.info("INIT: Start operation executor...");
    operationExecutor.startExecuting();
    LOGGER.info("INIT: DONE");
  }

  @PreDestroy
  public void shutdown() {
    if (DatabaseInfo.isElasticsearch()) {
      LOGGER.info("Shutdown elasticsearch clients.");
      ElasticsearchConnector.closeEsClient(esClient);
      ElasticsearchConnector.closeEsClient(zeebeEsClient);
    }
  }
}
