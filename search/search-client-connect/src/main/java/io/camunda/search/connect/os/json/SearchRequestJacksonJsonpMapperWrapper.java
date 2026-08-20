/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.os.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.search.connect.os.json.jackson.SearchAfterFieldJsonGenerator;
import jakarta.json.stream.JsonGenerator;
import org.opensearch.client.json.jackson.JacksonJsonpGenerator;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.core.SearchRequest;

public final class SearchRequestJacksonJsonpMapperWrapper extends JacksonJsonpMapper {

  public SearchRequestJacksonJsonpMapperWrapper(final ObjectMapper objectMapper) {
    super(objectMapper);
  }

  @Override
  public <T> void serialize(T value, JsonGenerator generator) {
    if (SearchRequest.class.isAssignableFrom(value.getClass())) {
      final var wrappedGenerator =
          new SearchAfterFieldJsonGenerator((JacksonJsonpGenerator) generator);
      super.serialize(value, wrappedGenerator);
    } else {
      super.serialize(value, generator);
    }
  }
}
