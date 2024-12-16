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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class CustomOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

  private static final DateTimeFormatter OFFSET_DATE_TIME_FORMATTER_NO_COLON =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXXX");

  private final DateTimeFormatter formatter;

  public CustomOffsetDateTimeDeserializer(final DateTimeFormatter formatter) {
    this.formatter = formatter;
  }

  @Override
  public OffsetDateTime deserialize(final JsonParser parser, final DeserializationContext context)
      throws IOException {

    OffsetDateTime parsedDate;
    try {
      // parse with the defined formatter, covering ~90% of all data reads
      parsedDate = OffsetDateTime.parse(parser.getText(), formatter);
    } catch (final DateTimeParseException dtpe1) {
      try {
        // try with the default colon-based formatter, full seconds offset and 'Z' allowed
        // this can be introduced by data writers that don't use the ConnectConfiguration
        parsedDate = OffsetDateTime.parse(parser.getText());
      } catch (final DateTimeParseException dtpe2) {
        try {
          // try with no colon offset, full seconds offset allowed, e.g. '...-083015'
          parsedDate = OffsetDateTime.parse(parser.getText(), OFFSET_DATE_TIME_FORMATTER_NO_COLON);
        } catch (final DateTimeParseException dtpe3) {
          parsedDate =
              ZonedDateTime.parse(
                      parser.getText(),
                      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                          .withZone(ZoneId.systemDefault()))
                  .toOffsetDateTime();
        }
      }
    }
    return parsedDate;
  }
}
