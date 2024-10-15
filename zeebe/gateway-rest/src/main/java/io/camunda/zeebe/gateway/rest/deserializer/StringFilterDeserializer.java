/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.node.TextNode;
import io.camunda.zeebe.gateway.protocol.rest.StringFilter;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.io.IOException;
import org.springframework.boot.jackson.JsonComponent;

@ConditionalOnRestGatewayEnabled
@JsonComponent
public class StringFilterDeserializer extends FilterDeserializer<StringFilter> {

  @Override
  public StringFilter deserialize(final JsonParser parser, final DeserializationContext context)
      throws IOException, JacksonException {

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
