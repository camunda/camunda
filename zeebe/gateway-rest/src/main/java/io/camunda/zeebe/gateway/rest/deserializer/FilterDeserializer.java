/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public abstract class FilterDeserializer<T> extends JsonDeserializer<T> {

  private final ObjectMapper objectMapper;

  public FilterDeserializer(final ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  protected <S extends T> S deserialize(final TreeNode node, final Class<S> clazz)
      throws IOException {
    return objectMapper.readValue(node.traverse(objectMapper), clazz);
  }
}
