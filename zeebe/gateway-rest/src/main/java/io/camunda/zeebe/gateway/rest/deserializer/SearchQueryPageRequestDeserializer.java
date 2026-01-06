/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ONLY_ONE_FIELD;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.gateway.protocol.rest.CursorBackwardPagination;
import io.camunda.zeebe.gateway.protocol.rest.CursorForwardPagination;
import io.camunda.zeebe.gateway.protocol.rest.LimitPagination;
import io.camunda.zeebe.gateway.protocol.rest.OffsetPagination;
import io.camunda.zeebe.gateway.protocol.rest.SearchQueryPageRequest;
import io.camunda.zeebe.gateway.rest.exception.DeserializationException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SearchQueryPageRequestDeserializer extends JsonDeserializer<SearchQueryPageRequest> {
  private static final String LIMIT_PAGINATION_FIELD = "limit";
  private static final String OFFSET_PAGINATION_FIELD = "from";
  private static final String AFTER_PAGINATION_KEY = "after";
  private static final String BEFORE_PAGINATION_KEY = "before";
  private static final Set<String> SUPPORTED_FIELDS =
      Set.of(
          OFFSET_PAGINATION_FIELD,
          AFTER_PAGINATION_KEY,
          BEFORE_PAGINATION_KEY,
          LIMIT_PAGINATION_FIELD);

  @Override
  public SearchQueryPageRequest deserialize(
      final JsonParser jsonParser, final DeserializationContext deserializationContext)
      throws IOException {
    final var codec = jsonParser.getCodec();
    final var treeNode = codec.readTree(jsonParser);

    final Set<String> presentFields = new HashSet<>();
    final var fields = treeNode.fieldNames();

    while (fields.hasNext()) {
      final String field = fields.next();
      if (SUPPORTED_FIELDS.contains(field) && !(treeNode.get(field) instanceof NullNode)) {
        presentFields.add(field);
      }
    }
    removeNullFields((ObjectNode) treeNode);
    // If the request contains only the limit field, we treat it as a LimitPagination request
    if (presentFields.contains(LIMIT_PAGINATION_FIELD) && presentFields.size() == 1) {
      return codec.treeToValue(treeNode, LimitPagination.class);
    }

    if (presentFields.isEmpty()
        || (!presentFields.contains(LIMIT_PAGINATION_FIELD) && presentFields.size() > 1)) {
      throw new DeserializationException(
          ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(getErrorMessageParam()));
    }

    if (presentFields.contains(OFFSET_PAGINATION_FIELD)) {
      return codec.treeToValue(treeNode, OffsetPagination.class);
    } else if (presentFields.contains(AFTER_PAGINATION_KEY)) {
      return codec.treeToValue(treeNode, CursorForwardPagination.class);
    }
    return codec.treeToValue(treeNode, CursorBackwardPagination.class);
  }

  private static void removeNullFields(final ObjectNode treeNode) {
    // Remove null fields from the tree to prevent parsing errors
    if (treeNode.get(OFFSET_PAGINATION_FIELD) instanceof NullNode) {
      treeNode.remove(OFFSET_PAGINATION_FIELD);
    }
    if (treeNode.get(AFTER_PAGINATION_KEY) instanceof NullNode) {
      treeNode.remove(AFTER_PAGINATION_KEY);
    }
    if (treeNode.get(BEFORE_PAGINATION_KEY) instanceof NullNode) {
      treeNode.remove(BEFORE_PAGINATION_KEY);
    }
    if (treeNode.get(LIMIT_PAGINATION_FIELD) instanceof NullNode) {
      treeNode.remove(LIMIT_PAGINATION_FIELD);
    }
  }

  private static String getErrorMessageParam() {
    return "[%s, %s, %s]"
        .formatted(OFFSET_PAGINATION_FIELD, AFTER_PAGINATION_KEY, BEFORE_PAGINATION_KEY);
  }
}
