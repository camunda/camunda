/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.SchemaManagerConfiguration;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("tasklistSchemaManager")
@Profile("test")
@Conditional(OpenSearchCondition.class)
public class TestOpenSearchSchemaManager implements TestSchemaManager, AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestOpenSearchSchemaManager.class);

  private final OpenSearchClient client;

  private final SearchEngineConfiguration configuration;

  private final ObjectMapper objectMapper;

  public TestOpenSearchSchemaManager(final SearchEngineConfiguration configuration) {
    final var connector = new OpensearchConnector(configuration.connect());
    client = connector.createClient();
    this.configuration = configuration;
    objectMapper = connector.objectMapper();
  }

  @Override
  public void createSchema() {
    final IndexDescriptors indexDescriptors =
        new IndexDescriptors(configuration.connect().getIndexPrefix(), false);
    final var searchEngineClient = new OpensearchEngineClient(client, objectMapper);
    // enable schema creation
    final var schemaManagerConfiguration = new SchemaManagerConfiguration();
    schemaManagerConfiguration.setCreateSchema(true);
    schemaManagerConfiguration.setRetry(configuration.schemaManager().getRetry());
    final SearchEngineConfiguration searchEngineConfiguration =
        SearchEngineConfiguration.of(
            b ->
                b.connect(configuration.connect())
                    .retention(configuration.retention())
                    .index(configuration.index())
                    .schemaManager(schemaManagerConfiguration));
    final var schemaManager =
        new SchemaManager(
            searchEngineClient,
            indexDescriptors.indices(),
            indexDescriptors.templates(),
            searchEngineConfiguration,
            objectMapper);
    schemaManager.startup();
  }

  @Override
  public void deleteSchemaQuietly() {
    try {
      deleteSchema();
    } catch (final Exception t) {
      LOGGER.debug(t.getMessage());
    }
  }

  public void deleteSchema() throws IOException {
    final String prefix = configuration.connect().getIndexPrefix();
    LOGGER.info("Removing indices " + prefix + "*");
    client.indices().delete(r -> r.index(prefix + "*"));
    client.indices().deleteTemplate(r -> r.name(prefix + "*"));
  }

  @Override
  public void close() throws Exception {
    client._transport().close();
  }
}
