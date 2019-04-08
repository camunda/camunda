/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es;

import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

import static org.camunda.optimize.service.util.ESVersionChecker.checkESVersionSupport;

public class SchemaInitializingElasticsearchRestClientFactory
  implements FactoryBean<RestHighLevelClient>, DisposableBean {
  private final static Logger logger = LoggerFactory.getLogger(SchemaInitializingElasticsearchRestClientFactory.class);

  private RestHighLevelClient esClient;

  private final ConfigurationService configurationService;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final BackoffCalculator backoffCalculator;

  @Autowired
  public SchemaInitializingElasticsearchRestClientFactory(final ConfigurationService configurationService,
                                                          final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                                          final BackoffCalculator backoffCalculator) {
    this.configurationService = configurationService;
    this.elasticSearchSchemaManager = elasticSearchSchemaManager;
    this.backoffCalculator = backoffCalculator;
  }


  @Override
  public RestHighLevelClient getObject() throws IOException {
    if (esClient == null) {
      logger.info("Initializing Elasticsearch rest client...");
      esClient = ElasticsearchHighLevelRestClientBuilder.build(configurationService);

      waitForElasticsearch(esClient);
      logger.info("Elasticsearch client has successfully been started");

      elasticSearchSchemaManager.validateExistingSchemaVersion(esClient);
      elasticSearchSchemaManager.initializeSchema(esClient);
    }
    return esClient;
  }

  private void waitForElasticsearch(RestHighLevelClient esClient) throws IOException {
    boolean isConnected = false;
    while (!isConnected) {
      try {
        isConnected = getNumberOfClusterNodes(esClient) > 0;
        if (!isConnected) {
          long sleepTime = backoffCalculator.calculateSleepTime();
          logger.info("No elasticsearch nodes available, waiting [{}] ms to retry connecting", sleepTime);
          Thread.sleep(sleepTime);
        }
      } catch (Exception e) {
        String message = "Can't connect to Elasticsearch. Please check the connection!";
        logger.error(message, e);
        throw new OptimizeRuntimeException(message, e);
      }
    }
    checkESVersionSupport(esClient);
  }

  private int getNumberOfClusterNodes(RestHighLevelClient esClient) {
    try {
      return esClient.cluster().health(new ClusterHealthRequest(), RequestOptions.DEFAULT).getNumberOfNodes();
    } catch (IOException e) {
      logger.error("Failed getting number of cluster nodes.", e);
      return 0;
    }
  }

  @Override
  public Class<?> getObjectType() {
    return RestHighLevelClient.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

  @Override
  public void destroy() {
    if (esClient != null) {
      try {
        esClient.close();
      } catch (IOException e) {
        logger.error("Could not close Elasticsearch client", e);
      }
    }
  }
}
