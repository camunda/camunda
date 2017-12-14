package org.camunda.optimize.service.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * @author Askar Akhmerov
 */
public class CustomDeserializer extends JsonDeserializer<OffsetDateTime> {

  private DateTimeFormatter formatter;

  public CustomDeserializer(DateTimeFormatter formatter) {
    this.formatter = formatter;
  }

  @Override
  public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException, JsonProcessingException {

    OffsetDateTime parsedDate;
    try {
      parsedDate = OffsetDateTime.parse(parser.getText(), this.formatter);
    } catch(DateTimeParseException exception) {
      //
      parsedDate = ZonedDateTime
        .parse(parser.getText(), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault()))
        .toOffsetDateTime();
    }
    return parsedDate;
  }
}