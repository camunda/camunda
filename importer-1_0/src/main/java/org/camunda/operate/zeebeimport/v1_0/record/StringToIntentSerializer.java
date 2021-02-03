/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.v1_0.record;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class StringToIntentSerializer extends JsonDeserializer<Intent> {
  @Override
  public Intent deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {

    final String stringValue = jsonParser.getText();

    if (stringValue != null && !stringValue.isEmpty()) {
      try {
        return Intent.valueOf(stringValue);
      } catch (IllegalArgumentException ex) {
        //
      }
    }
    return Intent.UNKNOWN;
  }
}
