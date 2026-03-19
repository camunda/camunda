/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.annotation.Generated;
import java.io.IOException;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedBasicStringFilterPropertyDeserializer
    extends JsonDeserializer<GeneratedBasicStringFilterPropertyStrictContract> {

  @Override
  public GeneratedBasicStringFilterPropertyStrictContract deserialize(
      final JsonParser p, final DeserializationContext ctxt) throws IOException {
    return switch (p.currentToken()) {
      case VALUE_STRING ->
          new GeneratedBasicStringFilterPropertyPlainValueStrictContract(p.getText());
      case START_OBJECT -> ctxt.readValue(p, GeneratedBasicStringFilterStrictContract.class);
      default ->
          throw InvalidFormatException.from(
              p, "Request property cannot be parsed", p.getText(), String.class);
    };
  }
}
