package org.camunda.optimize.service.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Askar Akhmerov
 */
public class CustomSerializer extends JsonSerializer<OffsetDateTime> {

  private DateTimeFormatter formatter;

  public CustomSerializer(DateTimeFormatter formatter) {
    this.formatter = formatter;
  }

  @Override
  public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException, JsonProcessingException {
    gen.writeString(value.format(this.formatter));
  }
}
