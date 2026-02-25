/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.data.os;

import io.camunda.configuration.conditions.ConditionalOnWebappEnabled;
import io.camunda.tasklist.data.DataGenerator;
import io.camunda.tasklist.data.DevDataGeneratorAbstract;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
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
@Conditional(OpenSearchCondition.class)
@ConditionalOnWebappEnabled("tasklist")
@DependsOn("searchEngineSchemaInitializer")
public class DevDataGeneratorOpenSearch extends DevDataGeneratorAbstract implements DataGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(DevDataGeneratorOpenSearch.class);

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient tasklistOsClient;

  @Override
  public boolean shouldCreateData() {
    try {

      final boolean exists =
          tasklistOsClient
                  .count(
                      b ->
                          b.index(
                              new ProcessIndex(
                                      tasklistProperties.getOpenSearch().getIndexPrefix(), false)
                                  .getFullQualifiedName()))
                  .count()
              > 0;

      if (exists) {
        // data already exists
        LOGGER.debug("Data already exists in Zeebe.");
        return false;
      }
    } catch (final IOException io) {
      LOGGER.debug(
          "Error occurred while checking existence of data in OS: {}. Demo data won't be created.",
          io.getMessage());
      return false;
    }
    return true;
  }
}
