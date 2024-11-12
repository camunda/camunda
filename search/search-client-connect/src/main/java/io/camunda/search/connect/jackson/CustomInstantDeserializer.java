/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;

public final class CustomInstantDeserializer extends JsonDeserializer<Instant> {

  @Override
  public Instant deserialize(final JsonParser parser, final DeserializationContext ctxt)
      throws IOException {
    return Instant.ofEpochMilli(Long.valueOf(parser.getText()));
  }
}
