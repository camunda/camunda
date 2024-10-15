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
import com.fasterxml.jackson.databind.node.NumericNode;
import io.camunda.zeebe.gateway.protocol.rest.LongFilter;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.io.IOException;
import org.springframework.boot.jackson.JsonComponent;

@ConditionalOnRestGatewayEnabled
@JsonComponent
public class LongFilterDeserializer extends FilterDeserializer<LongFilter> {

  @Override
  public LongFilter deserialize(final JsonParser parser, final DeserializationContext context)
      throws IOException, JacksonException {

    final var treeNode = parser.getCodec().readTree(parser);
    final var filter = new LongFilter();

    if (treeNode instanceof NumericNode) {
      filter.set$Eq(((NumericNode) treeNode).longValue());
      return filter;
    }

    // this part can be deserialized automatically
    return deserialize(treeNode, LongFilter.class);
  }
}
