/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class CustomOffsetDateTimeSerializer extends JsonSerializer<OffsetDateTime> {

  private DateTimeFormatter formatter;

  public CustomOffsetDateTimeSerializer(final DateTimeFormatter formatter) {
    this.formatter = formatter;
  }

  @Override
  public void serialize(
      final OffsetDateTime value, final JsonGenerator gen, final SerializerProvider provider)
      throws IOException {
    gen.writeString(value.format(formatter));
  }
}
