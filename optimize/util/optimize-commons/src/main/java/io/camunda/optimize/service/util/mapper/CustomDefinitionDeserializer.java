/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.mapper;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex;
import java.io.IOException;

public class CustomDefinitionDeserializer extends StdDeserializer<DefinitionOptimizeResponseDto> {

  private ObjectMapper objectMapper;

  public CustomDefinitionDeserializer(final ObjectMapper objectMapper) {
    this(DefinitionOptimizeResponseDto.class);
    this.objectMapper = objectMapper;
  }

  public CustomDefinitionDeserializer(final Class<?> vc) {
    super(vc);
  }

  @Override
  public DefinitionOptimizeResponseDto deserialize(
      final JsonParser jsonParser, final DeserializationContext deserializationContext)
      throws IOException {
    final JsonNode node = jsonParser.readValueAsTree();
    return deserialize(jsonParser, node);
  }

  public DefinitionOptimizeResponseDto deserialize(
      final JsonParser jsonParser, final JsonNode jsonNode) throws IOException {
    final DefinitionType definitionType = resolveDefinitionType(jsonNode);
    switch (definitionType) {
      case PROCESS:
        return objectMapper.readValue(jsonParser, ProcessDefinitionOptimizeDto.class);
      case DECISION:
        return objectMapper.readValue(jsonParser, DecisionDefinitionOptimizeDto.class);
      default:
        throw new JsonParseException(
            jsonParser,
            "Could not create definition object as it contains no specific xml property of a subclass.");
    }
  }

  private DefinitionType resolveDefinitionType(final JsonNode node) {
    return node.has(ProcessDefinitionIndex.PROCESS_DEFINITION_XML)
        ? DefinitionType.PROCESS
        : DefinitionType.DECISION;
  }
}
