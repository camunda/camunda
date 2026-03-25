/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/ProcessInstanceModificationTerminateInstruction
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
public final class GeneratedProcessInstanceModificationTerminateInstructionDeserializer
    extends JsonDeserializer<
        GeneratedProcessInstanceModificationTerminateInstructionStrictContract> {

  @Override
  public GeneratedProcessInstanceModificationTerminateInstructionStrictContract deserialize(
      final JsonParser p, final DeserializationContext ctxt) throws IOException {
    final JsonNode node = p.readValueAsTree();
    if (node.has("elementId")) {
      return p.getCodec()
          .treeToValue(
              node,
              GeneratedProcessInstanceModificationTerminateByIdInstructionStrictContract.class);
    }
    if (node.has("elementInstanceKey")) {
      return p.getCodec()
          .treeToValue(
              node,
              GeneratedProcessInstanceModificationTerminateByKeyInstructionStrictContract.class);
    }

    throw ValueInstantiationException.from(
        p,
        "At least one of [elementId, elementInstanceKey] is required",
        ctxt.constructType(
            GeneratedProcessInstanceModificationTerminateInstructionStrictContract.class),
        new IllegalArgumentException(
            "At least one of [elementId, elementInstanceKey] is required"));
  }
}
