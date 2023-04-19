/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v830.record;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class StringToIntentSerializer extends JsonDeserializer<Intent> {

  @Override
  public Intent deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException, JsonProcessingException {

    final String stringValue = jsonParser.getText();

    if (stringValue != null && !stringValue.isEmpty()) {
      try {
        return Intent.valueOf(stringValue);
      } catch (IllegalArgumentException ex) {
        // ignore me
      }
    }
    return Intent.UNKNOWN;
  }
}
