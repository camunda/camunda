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
import com.fasterxml.jackson.databind.node.IntNode;
import io.camunda.zeebe.gateway.protocol.rest.AdvancedIntegerFilter;
import io.camunda.zeebe.gateway.protocol.rest.IntegerFilter;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.io.IOException;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Lazy;

@ConditionalOnRestGatewayEnabled
@JsonComponent
public class IntegerFilterDeserializer extends FilterDeserializer<IntegerFilter> {

  public IntegerFilterDeserializer(@Lazy final ObjectMapper objectMapper) {
    super(objectMapper);
  }

  @Override
  public IntegerFilter deserialize(final JsonParser parser, final DeserializationContext context)
      throws IOException {

    final var treeNode = parser.getCodec().readTree(parser);

    if (treeNode instanceof IntNode) {
      final var filter = new AdvancedIntegerFilter();
      filter.set$Eq(((IntNode) treeNode).intValue());
      return filter;
    }

    // this part can be deserialized automatically
    return deserialize(treeNode, AdvancedIntegerFilter.class);
  }
}
