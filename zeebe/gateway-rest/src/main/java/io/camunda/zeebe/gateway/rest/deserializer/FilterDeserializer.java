/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.ValueNode;
import java.io.IOException;

/**
 * @param <T> Base type to register deserializer for.
 * @param <E> Explicit value type.
 */
public abstract class FilterDeserializer<T, E> extends JsonDeserializer<T> {

  protected <C> C deserialize(final ObjectCodec codec, final TreeNode node, final Class<C> clazz)
      throws IOException {
    return codec.readValue(node.traverse(codec), clazz);
  }

  /** Actual type that the deserializer finally returns. */
  protected abstract Class<? extends T> getFinalType();

  /** Implicit filter type. */
  protected abstract Class<E> getImplicitValueType();

  /** Create filter from implicit value. */
  protected abstract T createFromImplicitValue(E value);

  @Override
  public T deserialize(final JsonParser parser, final DeserializationContext context)
      throws IOException {

    final var codec = parser.getCodec();
    final var treeNode = codec.readTree(parser);

    if (treeNode instanceof ValueNode) {
      return createFromImplicitValue(deserialize(codec, treeNode, getImplicitValueType()));
    }

    return deserialize(codec, treeNode, getFinalType());
  }
}
