/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.StringReader;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;

/**
 * The OpenSearch {@link DocumentSchemaClient}: mirrors the Elasticsearch one — deserializes the
 * hand-rolled JSON mapping into the OpenSearch {@link TypeMapping} and creates the index
 * idempotently.
 */
public final class OpensearchSchemaClient implements DocumentSchemaClient {

  private final OpenSearchClient client;

  public OpensearchSchemaClient(final OpenSearchClient client) {
    this.client = client;
  }

  @Override
  public void createIndex(final String index, final String mappingJson) {
    try {
      if (client.indices().exists(e -> e.index(index)).value()) {
        return;
      }
      final JsonpMapper mapper = client._transport().jsonpMapper();
      final TypeMapping mapping;
      try (JsonParser parser = mapper.jsonProvider().createParser(new StringReader(mappingJson))) {
        mapping = TypeMapping._DESERIALIZER.deserialize(parser, mapper);
      }
      client.indices().create(c -> c.index(index).mappings(mapping));
    } catch (final IOException e) {
      throw new IllegalStateException("failed to create OpenSearch index " + index, e);
    }
  }
}
