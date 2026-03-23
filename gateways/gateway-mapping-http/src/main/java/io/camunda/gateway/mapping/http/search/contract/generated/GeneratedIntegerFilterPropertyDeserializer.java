/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/filters.yaml#/components/schemas/IntegerFilterProperty
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.annotation.Generated;
import java.io.IOException;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedIntegerFilterPropertyDeserializer
    extends JsonDeserializer<GeneratedIntegerFilterPropertyStrictContract> {

  @Override
  public GeneratedIntegerFilterPropertyStrictContract deserialize(
      final JsonParser p, final DeserializationContext ctxt) throws IOException {
    return switch (p.currentToken()) {
      case VALUE_NUMBER_INT ->
          new GeneratedIntegerFilterPropertyPlainValueStrictContract(p.getIntValue());
      case VALUE_STRING -> {
        try {
          yield new GeneratedIntegerFilterPropertyPlainValueStrictContract(
              Integer.parseInt(p.getText()));
        } catch (NumberFormatException e) {
          throw InvalidFormatException.from(
              p, "Expected integer value for filter property", p.getText(), Integer.class);
        }
      }
      case START_OBJECT -> ctxt.readValue(p, GeneratedAdvancedIntegerFilterStrictContract.class);
      default ->
          throw InvalidFormatException.from(
              p, "Request property cannot be parsed", p.getText(), Integer.class);
    };
  }
}
