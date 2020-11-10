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
import java.time.format.DateTimeFormatter;


public class CustomOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

  private final DateTimeFormatter formatter;

  public CustomOffsetDateTimeDeserializer(DateTimeFormatter formatter) {
    this.formatter = formatter;
  }

  @Override
  public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    return OffsetDateTime.parse(parser.getText(), this.formatter);
  }
}