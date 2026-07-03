/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.json.JsonpMapper;
import jakarta.json.stream.JsonParser;
import java.io.IOException;
import java.io.StringReader;

/**
 * The Elasticsearch {@link DocumentSchemaClient}: creates an index from a hand-rolled JSON mapping
 * by deserializing it into the client's {@link TypeMapping} (the same mapper OC's search client
 * uses) and issuing an idempotent create.
 */
public final class ElasticsearchSchemaClient implements DocumentSchemaClient {

  private final ElasticsearchClient client;

  public ElasticsearchSchemaClient(final ElasticsearchClient client) {
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
      throw new IllegalStateException("failed to create Elasticsearch index " + index, e);
    }
  }
}
