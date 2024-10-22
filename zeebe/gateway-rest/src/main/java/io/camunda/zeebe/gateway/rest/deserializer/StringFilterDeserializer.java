/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.camunda.zeebe.gateway.protocol.rest.StringFilter;
import java.io.IOException;

public class StringFilterDeserializer extends FilterDeserializer<StringFilter> {

  public StringFilterDeserializer(final ObjectMapper objectMapper) {
    super(objectMapper);
  }

  @Override
  public StringFilter deserialize(final JsonParser parser, final DeserializationContext context)
      throws IOException {

    final var treeNode = parser.getCodec().readTree(parser);
    final var filter = new StringFilter();

    if (treeNode instanceof TextNode) {
      filter.set$Eq(((TextNode) treeNode).textValue());
      return filter;
    }

    // this part can be deserialized automatically
    return deserialize(treeNode, StringFilter.class);
  }
}
