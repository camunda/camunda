/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Accumulates the results of input mappings that are evaluated one by one in modeling order. Each
 * mapping's result is stored under its (possibly nested) target path, and can be looked up by its
 * top-level variable name so that subsequent mappings can reference the values produced by earlier
 * mappings. {@link #toDocument()} returns the accumulated results as a single MsgPack document to
 * be merged into the element's local scope.
 *
 * <p>A mapping to a nested target descends into (or creates) intermediate objects; a mapping whose
 * target collides structurally with an earlier one (a value where an object is needed, or vice
 * versa) replaces the earlier entry — the same last-wins behavior the previous combined mapping
 * expression had.
 */
@NullMarked
public final class InputMappingResultBuilder {

  /** Values are either a nested {@code Map<String, Object>} or a MsgPack {@link DirectBuffer}. */
  private final Map<String, Object> entries = new LinkedHashMap<>();

  private final MsgPackWriter writer = new MsgPackWriter();

  /**
   * Puts a copy of the given MsgPack value at the nested target path. The value buffer is copied
   * because evaluation result buffers are transient and may be reused by the next evaluation.
   */
  public void put(final List<String> targetPath, final DirectBuffer value) {
    Map<String, Object> current = entries;
    for (int i = 0; i < targetPath.size() - 1; i++) {
      current = getOrAddNested(current, targetPath.get(i));
    }
    current.put(targetPath.getLast(), BufferUtil.cloneBuffer(value));
  }

  /**
   * Returns the accumulated value of the given top-level variable, or {@code null} if no mapping
   * has produced it yet. Nested structures are serialized to a MsgPack document on lookup.
   */
  public @Nullable DirectBuffer getVariable(final String name) {
    final var entry = entries.get(name);
    if (entry == null) {
      return null;
    } else if (entry instanceof final DirectBuffer value) {
      return value;
    } else {
      return serialize(asNestedMap(entry));
    }
  }

  /** Returns all accumulated results as a single MsgPack document (a map). */
  public DirectBuffer toDocument() {
    return serialize(entries);
  }

  private static Map<String, Object> getOrAddNested(
      final Map<String, Object> parent, final String key) {
    if (parent.get(key) instanceof Map<?, ?>) {
      return asNestedMap(parent.get(key));
    }
    final var nested = new LinkedHashMap<String, Object>();
    parent.put(key, nested);
    return nested;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asNestedMap(final Object entry) {
    return (Map<String, Object>) entry;
  }

  private DirectBuffer serialize(final Map<String, Object> map) {
    final var buffer = new ExpandableArrayBuffer();
    writer.wrap(buffer, 0);
    writeMap(map);
    return new UnsafeBuffer(buffer, 0, writer.getOffset());
  }

  /**
   * Writes a (possibly deeply nested) result map to msgpack using an iterative depth-first
   * traversal instead of recursion. A {@code zeebe:input} target path can have an unbounded number
   * of '.'-separated segments (ZeebeExpressionValidator's path pattern doesn't cap it), and this
   * runs on every element activation — plain recursion here previously let a deeply-nested target
   * throw an uncaught StackOverflowError before NestingDepthValidator ever got a chance to reject
   * the document gracefully.
   */
  private void writeMap(final Map<String, Object> root) {
    final Deque<Iterator<Map.Entry<String, Object>>> pending = new ArrayDeque<>();
    writer.writeMapHeader(root.size());
    pending.push(root.entrySet().iterator());

    while (!pending.isEmpty()) {
      final var iterator = pending.peek();
      if (!iterator.hasNext()) {
        pending.pop();
        continue;
      }
      final var entry = iterator.next();
      writer.writeString(BufferUtil.wrapString(entry.getKey()));
      final var value = entry.getValue();
      if (value instanceof Map<?, ?>) {
        final var nested = asNestedMap(value);
        writer.writeMapHeader(nested.size());
        pending.push(nested.entrySet().iterator());
      } else {
        writer.writeRaw((DirectBuffer) value);
      }
    }
  }
}
