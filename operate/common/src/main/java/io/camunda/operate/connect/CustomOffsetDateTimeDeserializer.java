/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
