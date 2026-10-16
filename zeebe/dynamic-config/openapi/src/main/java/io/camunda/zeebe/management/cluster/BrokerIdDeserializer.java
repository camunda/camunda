/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.management.cluster;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

class BrokerIdDeserializer extends StdDeserializer<BrokerId> {

  BrokerIdDeserializer() {
    super(BrokerId.class);
  }

  @Override
  public BrokerId deserialize(final JsonParser p, final DeserializationContext ctx)
      throws IOException {
    if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
      return new BrokerId.Integer(p.getIntValue());
    } else if (p.currentToken() == JsonToken.VALUE_STRING) {
      return new BrokerId.String(p.getText());
    } else {
      throw new JsonParseException(
          p, "Expected integer or string broker ID, got: " + p.currentToken());
    }
  }
}
