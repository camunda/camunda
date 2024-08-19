/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.mapper;

import com.ethlo.time.ITU;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import org.slf4j.Logger;

public class CustomCloudEventTimeDeserializer extends JsonDeserializer<Instant> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(CustomCloudEventTimeDeserializer.class);

  @Override
  public Instant deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    if (p.currentTokenId() == JsonTokenId.ID_STRING) {
      try {
        return ITU.parseDateTime(p.getText()).toInstant();
      } catch (final DateTimeException ex) {
        log.warn("There was a problem creating Instant from {}", p.getText());
      }
    }
    throw new JsonParseException(
        p,
        "Could not create Instant from the time provided, it must be a string in valid RFC3339 format: "
            + p.getText());
  }
}
