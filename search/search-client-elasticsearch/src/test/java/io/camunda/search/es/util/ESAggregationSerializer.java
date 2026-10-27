/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.util;

import co.elastic.clients.json.JsonpSerializable;
import co.elastic.clients.json.jackson.JacksonJsonpGenerator;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;

public class ESAggregationSerializer implements AutoCloseable {

  private final JacksonJsonpMapper mapper;
  private final JacksonJsonpGenerator jacksonJsonpGenerator;
  private final StringWriter out;

  public ESAggregationSerializer() throws IOException {
    // To serialize OS aggregations to JSON
    out = new StringWriter();
    final BufferedWriter w = new BufferedWriter(out);
    final JsonGenerator jsonGenerator = new JsonFactory().createGenerator(w);

    jacksonJsonpGenerator = new JacksonJsonpGenerator(jsonGenerator);
    mapper = new JacksonJsonpMapper(new ObjectMapper());
  }

  /**
   * Serializes the given aggregation into a JSON string.
   *
   * @param serializable the aggregation object that implements JsonpSerializable.
   * @return the JSON string representation of the aggregation.
   */
  public String serialize(final JsonpSerializable serializable) {
    serializable.serialize(jacksonJsonpGenerator, mapper);
    jacksonJsonpGenerator.flush();
    return out.toString();
  }

  @Override
  public void close() throws Exception {
    out.close();
    jacksonJsonpGenerator.close();
  }
}
