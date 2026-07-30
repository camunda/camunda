/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.msgpack.spec.MsgPackCodes;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Accumulates the results of variable mappings that are evaluated one by one in modeling order.
 * Each mapping's result is stored under its (possibly nested) target path, and can be looked up by
 * its top-level variable name so that subsequent mappings can reference the values produced by
 * earlier mappings. {@link #toDocument()} returns the accumulated results as a single MsgPack
 * document to be merged into the element's local scope.
 *
 * <p>For input mappings (no-arg constructor), a mapping to a nested target descends into (or
 * creates) intermediate objects; a mapping whose target collides structurally with an earlier one
 * (a value where an object is needed, or vice versa) replaces the earlier entry — last-wins.
 *
 * <p>For output mappings (constructor with a {@code scopeValueResolver}), a nested target instead
 * merges with the existing scope variable at every path level: a level that is absent or holds a
 * plain value is seeded from the current scope value at that path. A non-context scope value
 * poisons the level to null, matching FEEL's {@code context merge(<non-context>, {...})} behavior.
 */
@NullMarked
public final class MappingResultBuilder {

  /**
   * Marks a nested level whose existing scope value was not a context: the level evaluates to null,
   * matching FEEL's {@code context merge(<non-context>, {...})}. Serialized as NIL.
   */
  private static final Object POISON = new Object();

  private static final DirectBuffer NIL = new UnsafeBuffer(new byte[] {MsgPackCodes.NIL});

  /**
   * Values are either a nested {@code Map<String, Object>}, {@link #POISON}, or a MsgPack {@link
   * DirectBuffer}.
   */
  private final Map<String, Object> entries = new LinkedHashMap<>();

  private final MsgPackWriter writer = new MsgPackWriter();

  private final Function<List<String>, @Nullable DirectBuffer> scopeValueResolver;

  /** Builder for input mappings: nested targets never merge with existing scope values. */
  public MappingResultBuilder() {
    this(path -> null);
  }

  /**
   * Builder for output mappings: when a nested target path needs a map at a level that is absent
   * (or currently holds a plain value), the level is seeded from the existing scope value at that
   * path. A non-context scope value poisons the level to null, matching FEEL's {@code context
   * merge} behavior.
   *
   * @param scopeValueResolver resolves a target-path prefix to the current scope value at that
   *     path, or {@code null} when there is none; invoked only when a level must be (re)created
   */
  public MappingResultBuilder(
      final Function<List<String>, @Nullable DirectBuffer> scopeValueResolver) {
    this.scopeValueResolver = scopeValueResolver;
  }

  /**
   * Puts a copy of the given MsgPack value at the nested target path. The value buffer is copied
   * because evaluation result buffers are transient and may be reused by the next evaluation.
   */
  public void put(final List<String> targetPath, final DirectBuffer value) {
    Map<String, Object> current = entries;
    for (int i = 0; i < targetPath.size() - 1; i++) {
      final var next = getOrSeedNested(current, targetPath.subList(0, i + 1));
      if (next == null) {
        return; // level is poisoned: the mapped value is discarded, the level stays null
      }
      current = next;
    }
    current.put(targetPath.getLast(), BufferUtil.cloneBuffer(value));
  }

  /**
   * Returns the accumulated value of the given top-level variable, or {@code null} if no mapping
   * has produced it yet. Nested structures are serialized to a MsgPack document on lookup. A
   * poisoned top-level entry returns a NIL buffer (not Java {@code null}, which would let the
   * caller fall back to the scope lookup instead of seeing the poisoned null).
   */
  public @Nullable DirectBuffer getVariable(final String name) {
    final var entry = entries.get(name);
    if (entry == null) {
      return null;
    } else if (entry == POISON) {
      return NIL;
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

  /**
   * Returns the map at the given path prefix, creating it if the current entry is absent or a plain
   * value. A newly created map is seeded with the top-level entries of the existing scope value at
   * that path (children stay opaque buffers); a non-context scope value poisons the level instead.
   * Returns {@code null} when the level is (or becomes) poisoned.
   */
  private @Nullable Map<String, Object> getOrSeedNested(
      final Map<String, Object> parent, final List<String> pathPrefix) {
    final var key = pathPrefix.getLast();
    final var entry = parent.get(key);
    if (entry == POISON) {
      return null;
    }
    if (entry instanceof Map<?, ?>) {
      return asNestedMap(entry);
    }
    // absent, or a plain value from an earlier mapping: re-seed the (re)created level from the
    // scope value at this path, dropping the earlier plain value
    final var scopeValue = scopeValueResolver.apply(pathPrefix);
    if (scopeValue == null || isNil(scopeValue)) {
      final var fresh = new LinkedHashMap<String, Object>();
      parent.put(key, fresh);
      return fresh;
    }
    if (!MsgPackCodes.isMap(scopeValue.getByte(0))) {
      parent.put(key, POISON);
      return null;
    }
    final var seeded = deserializeTopLevel(scopeValue);
    parent.put(key, seeded);
    return seeded;
  }

  private static boolean isNil(final DirectBuffer value) {
    return value.capacity() == 1 && value.getByte(0) == MsgPackCodes.NIL;
  }

  /** Reads the top level of a msgpack map into a builder map; values stay opaque cloned slices. */
  private static Map<String, Object> deserializeTopLevel(final DirectBuffer map) {
    final var result = new LinkedHashMap<String, Object>();
    final var reader = new MsgPackReader();
    reader.wrap(map, 0, map.capacity());
    final int entryCount = reader.readMapHeader();
    for (int i = 0; i < entryCount; i++) {
      final var key = BufferUtil.bufferAsString(reader.readToken().getValueBuffer());
      final int valueOffset = reader.getOffset();
      reader.skipValue();
      final var slice = new UnsafeBuffer(map, valueOffset, reader.getOffset() - valueOffset);
      result.put(key, BufferUtil.cloneBuffer(slice));
    }
    return result;
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
      } else if (value == POISON) {
        writer.writeNil();
      } else {
        writer.writeRaw((DirectBuffer) value);
      }
    }
  }
}
