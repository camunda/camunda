/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp;

import io.camunda.tasklist.data.DataGenerator;
import io.camunda.tasklist.es.ElasticsearchConnector;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.es.ElasticsearchUserDetailsService;
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
