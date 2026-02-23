/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.data.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.configuration.conditions.ConditionalOnWebappEnabled;
import io.camunda.tasklist.data.DataGenerator;
import io.camunda.tasklist.data.DevDataGeneratorAbstract;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev-data")
@Conditional(ElasticSearchCondition.class)
@ConditionalOnWebappEnabled("tasklist")
@DependsOn("searchEngineSchemaInitializer")
public class DevDataGeneratorElasticSearch extends DevDataGeneratorAbstract
    implements DataGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(DevDataGeneratorElasticSearch.class);

  @Autowired
  @Qualifier("tasklistEsClient")
  private ElasticsearchClient esClient;

  @Override
  public boolean shouldCreateData() {
    try {
      final String indexName =
          new ProcessIndex(tasklistProperties.getElasticsearch().getIndexPrefix(), true)
              .getFullQualifiedName();
      final boolean exists = esClient.count(c -> c.index(indexName)).count() > 0;
      if (exists) {
        // data already exists
        LOGGER.debug("Data already exists.");
        return false;
      }
    } catch (final IOException io) {
      LOGGER.debug(
          "Error occurred while checking existence of data in ES: {}. Demo data won't be created.",
          io.getMessage());
      return false;
    }
    return true;
  }
}
