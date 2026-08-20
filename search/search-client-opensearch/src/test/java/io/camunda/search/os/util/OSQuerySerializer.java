/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.util;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;

public class OSQuerySerializer implements AutoCloseable {
  private final JacksonJsonpMapper mapper;
  private final JacksonJsonpGenerator jacksonJsonpGenerator;
  private final StringWriter out;

  public OSQuerySerializer() throws IOException {

    // To serialize OS queries to json
    out = new StringWriter();
    final BufferedWriter w = new BufferedWriter(out);
    final JsonGenerator jsonGenerator = new JsonFactory().createGenerator(w);

    jacksonJsonpGenerator = new JacksonJsonpGenerator(jsonGenerator);
    mapper = new JacksonJsonpMapper(new ObjectMapper());
  }

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
