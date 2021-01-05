/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class CustomOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

  public static final String OFFSET_X_DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSX";
  private final DateTimeFormatter formatter;

  public CustomOffsetDateTimeDeserializer(DateTimeFormatter formatter) {
    this.formatter = formatter;
  }

  @Override
  public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    try {
      return OffsetDateTime.parse(parser.getText(), this.formatter);
    } catch (DateTimeParseException exception) {
      // If the offset is a 'Z', we can handle using a backup `X` pattern rather than failing
      return ZonedDateTime
        .parse(
          parser.getText(),
          DateTimeFormatter.ofPattern(OFFSET_X_DATE_TIME_PATTERN)
            .withZone(ZoneId.systemDefault())
        )
        .toOffsetDateTime();
    }
  }

}