/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/SourceElementInstruction
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import jakarta.annotation.Generated;
import java.io.IOException;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public final class GeneratedSourceElementInstructionDeserializer
    extends JsonDeserializer<GeneratedSourceElementInstructionStrictContract> {

  @Override
  public GeneratedSourceElementInstructionStrictContract deserialize(
      final JsonParser p, final DeserializationContext ctxt) throws IOException {
    final JsonNode node = p.readValueAsTree();
    if (node.has("sourceElementId")) {
      return p.getCodec()
          .treeToValue(node, GeneratedSourceElementIdInstructionStrictContract.class);
    }
    if (node.has("sourceElementInstanceKey")) {
      return p.getCodec()
          .treeToValue(node, GeneratedSourceElementInstanceKeyInstructionStrictContract.class);
    }

    throw ValueInstantiationException.from(
        p,
        "At least one of [sourceElementId, sourceElementInstanceKey] is required",
        ctxt.constructType(GeneratedSourceElementInstructionStrictContract.class),
        new IllegalArgumentException(
            "At least one of [sourceElementId, sourceElementInstanceKey] is required"));
  }
}
