/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.record;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class StringToIntentSerializer extends JsonDeserializer<Intent> {

  @Override
  public Intent deserialize(
      final JsonParser jsonParser, final DeserializationContext deserializationContext)
      throws IOException, JsonProcessingException {

    final String stringValue = jsonParser.getText();

    if (stringValue != null && !stringValue.isEmpty()) {
      try {
        return Intent.valueOf(stringValue);
      } catch (final IllegalArgumentException ex) {
        // ignore me
      }
    }
    return Intent.UNKNOWN;
  }
}
