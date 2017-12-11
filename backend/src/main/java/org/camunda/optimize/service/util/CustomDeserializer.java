package org.camunda.optimize.service.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

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
    return OffsetDateTime.parse(parser.getText(), this.formatter);
  }
}