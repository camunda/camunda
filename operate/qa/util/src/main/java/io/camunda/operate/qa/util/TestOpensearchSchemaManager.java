/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.zeebe.util.retry.RetryDecorator;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("schemaManager")
@Conditional(OpensearchCondition.class)
@Profile("test")
public class TestOpensearchSchemaManager implements TestSchemaManager, AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestOpensearchSchemaManager.class);

  private final OpenSearchClient client;

  private final SearchEngineConfiguration configuration;

  private final RetryDecorator retryDecorator;

  public TestOpensearchSchemaManager(final SearchEngineConfiguration configuration) {
    client = new OpensearchConnector(configuration.connect()).createClient();
    this.configuration = configuration;
    retryDecorator =
        new RetryDecorator().withRetryOnExceptions(OpenSearchException.class, IOException.class);
  }

  private void deleteSchema() throws IOException {
    final String prefix = configuration.connect().getIndexPrefix();
    LOGGER.info("Removing indices {}*", prefix);
    client.indices().delete(r -> r.index(prefix + "*"));
    client.indices().deleteTemplate(r -> r.name(prefix + "*"));
  }

  @Override
  public void deleteSchemaQuietly() {
    try {
      deleteSchema();
    } catch (final Exception t) {
      LOGGER.debug(t.getMessage());
    }
  }

  @Override
  public void refresh(final String indexPattern) {
    retryDecorator.decorateCheckedRunnable(
        "refresh indices '%s'".formatted(indexPattern),
        () -> client.indices().refresh(r -> r.index(indexPattern)));
  }

  @Override
  public void close() throws Exception {
    client._transport().close();
  }
}
