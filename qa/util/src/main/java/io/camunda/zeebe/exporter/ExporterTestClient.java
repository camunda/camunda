/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExporterTestClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExporterTestClient.class);

  private final ElasticsearchExporterConfiguration config;
  private final RestClient restClient;

  public ExporterTestClient(final ElasticsearchExporterConfiguration config) {
    this.config = config;
    restClient = RestClientFactory.of(config);
  }

  public void deleteIndices() {
    try {
      final String indexPrefix = config.index.prefix;
      LOGGER.info("Removing indices {}*", indexPrefix);
      final var request = new Request("DELETE", indexPrefix + "*");
      restClient.performRequest(request);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
