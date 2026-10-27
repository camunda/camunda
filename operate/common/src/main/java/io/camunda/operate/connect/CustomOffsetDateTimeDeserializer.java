/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

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

  private DateTimeFormatter formatter;

  public CustomOffsetDateTimeDeserializer(DateTimeFormatter formatter) {
    this.formatter = formatter;
  }

  @Override
  public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {

    OffsetDateTime parsedDate;
    try {
      parsedDate = OffsetDateTime.parse(parser.getText(), this.formatter);
    } catch (DateTimeParseException exception) {
      //
      parsedDate =
          ZonedDateTime.parse(
                  parser.getText(),
                  DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                      .withZone(ZoneId.systemDefault()))
              .toOffsetDateTime();
    }
    return parsedDate;
  }
}
