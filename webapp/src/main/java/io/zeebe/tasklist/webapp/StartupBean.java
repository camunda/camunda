/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp;

import io.zeebe.tasklist.data.DataGenerator;
import io.zeebe.tasklist.es.ElasticsearchConnector;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.webapp.security.es.ElasticsearchUserDetailsService;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
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

  @Autowired private RestHighLevelClient esClient;

  @Autowired private RestHighLevelClient zeebeEsClient;

  @Autowired(required = false)
  private ElasticsearchUserDetailsService elasticsearchUserDetailsService;

  @Autowired private DataGenerator dataGenerator;

  @Autowired private TasklistProperties tasklistProperties;

  @PostConstruct
  public void initApplication() {
    if (elasticsearchUserDetailsService != null) {
      LOGGER.info("INIT: Create users in elasticsearch if not exists ...");
      elasticsearchUserDetailsService.initializeUsers();
    }
    LOGGER.debug("INIT: Generate demo data...");
    try {
      dataGenerator.createZeebeDataAsync();
    } catch (Exception ex) {
      LOGGER.debug("Demo data could not be generated. Cause: {}", ex.getMessage());
      LOGGER.error("Error occurred when generating demo data.", ex);
    }
    LOGGER.info("INIT: DONE");
  }

  @PreDestroy
  public void shutdown() {
    LOGGER.info("Shutdown elasticsearch clients.");
    ElasticsearchConnector.closeEsClient(esClient);
    ElasticsearchConnector.closeEsClient(zeebeEsClient);
  }
}
