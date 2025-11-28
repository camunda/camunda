/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ONLY_ONE_FIELD;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.gateway.rest.exception.DeserializationException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractRequestDeserializer<T> extends JsonDeserializer<T> {

  protected abstract List<String> getSupportedFields();

  protected abstract Class<? extends T> getResultType(Set<String> presentFields);

  @Override
  public T deserialize(
      final JsonParser jsonParser, final DeserializationContext deserializationContext)
      throws IOException {
    final var codec = jsonParser.getCodec();
    final var treeNode = codec.readTree(jsonParser);

    final Set<String> presentFields = new HashSet<>();
    final var fields = treeNode.fieldNames();

    while (fields.hasNext()) {
      final String field = fields.next();
      if (getSupportedFields().contains(field) && !(treeNode.get(field) instanceof NullNode)) {
        presentFields.add(field);
      }
    }

    validateFields(presentFields);

    // Remove null fields from the tree to prevent parsing errors
    getSupportedFields()
        .forEach(
            field -> {
              if (treeNode.get(field) instanceof NullNode) {
                ((ObjectNode) treeNode).remove(field);
              }
            });

    return codec.treeToValue(treeNode, getResultType(presentFields));
  }

  protected void validateFields(final Set<String> presentFields) {
    if (presentFields.size() > 1) {
      throw new DeserializationException(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(getSupportedFields()));
    }
    if (presentFields.isEmpty()) {
      throw new DeserializationException(
          ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(getSupportedFields()));
    }
  }
}
