/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.mapper;

import com.ethlo.time.ITU;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;

@Slf4j
public class CustomCloudEventTimeDeserializer extends JsonDeserializer<Instant> {

  @Override
  public Instant deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException {
    if (p.currentTokenId() == JsonTokenId.ID_STRING) {
      try {
        return ITU.parseDateTime(p.getText()).toInstant();
      } catch (DateTimeException ex) {
        log.warn("There was a problem creating Instant from {}", p.getText());
      }
    }
    throw new JsonParseException(
      p, "Could not create Instant from the time provided, it must be a string in valid RFC3339 format: " + p.getText()
    );
  }
}
