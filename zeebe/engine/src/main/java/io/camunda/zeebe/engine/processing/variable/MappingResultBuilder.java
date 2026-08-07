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
import java.util.ArrayList;
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
 * {@link #getVariable(String)} under its top-level variable name so that subsequent mappings can
 * reference the values produced by earlier mappings.
 *
 * <p>That lookup view and the document returned by {@link #toDocument()} are built separately and
 * can differ. The lookup view is seeded from the element's scope chain, so a mapping's source
 * expression can read any branch visible to the element. The document contains only the paths a
 * mapping explicitly wrote, seeded from the scope the document is merged into.
 *
 * <p>For input mappings (no-arg constructor), a mapping to a nested target descends into (or
 * creates) intermediate objects; a mapping whose target collides structurally with an earlier one
 * (a value where an object is needed, or vice versa) replaces the earlier entry — last-wins. Both
 * resolvers are empty there, so neither view merges with an existing scope value.
 *
 * <p>For output mappings (two-resolver constructor), a nested target instead merges with the
 * existing scope variable at every path level: a level that is absent or holds a plain value is
 * seeded from the resolved scope value at that path. A non-context scope value poisons the level to
 * null, matching FEEL's {@code context merge(<non-context>, {...})} behavior.
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

  private final Function<List<String>, @Nullable DirectBuffer> scopeChainResolver;
  private final Function<List<String>, @Nullable DirectBuffer> mergeTargetResolver;

  /** Recorded in modeling order; replayed by {@link #toDocument()} against the merge target. */
  private final List<Map.Entry<List<String>, DirectBuffer>> writes = new ArrayList<>();

  /** Builder for input mappings: nested targets never merge with existing scope values. */
  public MappingResultBuilder() {
    this(path -> null, path -> null);
  }

  /**
   * Builder for output mappings. See the class doc for why two resolvers are needed.
   *
   * @param scopeChainResolver resolves a target-path prefix against the element's scope chain
   * @param mergeTargetResolver resolves a target-path prefix against the scope the result is merged
   *     into
   */
  public MappingResultBuilder(
      final Function<List<String>, @Nullable DirectBuffer> scopeChainResolver,
      final Function<List<String>, @Nullable DirectBuffer> mergeTargetResolver) {
    this.scopeChainResolver = scopeChainResolver;
    this.mergeTargetResolver = mergeTargetResolver;
  }

  /**
   * Puts a copy of the given MsgPack value at the nested target path. The value buffer is copied
   * because evaluation result buffers are transient and may be reused by the next evaluation.
   */
  public void put(final List<String> targetPath, final DirectBuffer value) {
    final var copy = BufferUtil.cloneBuffer(value);
    writes.add(Map.entry(targetPath, copy));
    apply(entries, targetPath, copy, scopeChainResolver);
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

  /**
   * Returns the paths that mappings explicitly wrote as a single MsgPack document, replayed against
   * the merge-target resolver rather than the {@link #getVariable(String)} lookup view.
   *
   * @see #getVariable(String)
   * @see <a href="https://github.com/camunda/camunda/issues/35251">#35251</a>
   */
  public DirectBuffer toDocument() {
    final Map<String, Object> emitted = new LinkedHashMap<>();
    for (final var write : writes) {
      apply(emitted, write.getKey(), write.getValue(), mergeTargetResolver);
    }
    return serialize(emitted);
  }

  private void apply(
      final Map<String, Object> root,
      final List<String> targetPath,
      final DirectBuffer value,
      final Function<List<String>, @Nullable DirectBuffer> resolver) {
    Map<String, Object> current = root;
    for (int i = 0; i < targetPath.size() - 1; i++) {
      final var next = getOrSeedNested(current, targetPath.subList(0, i + 1), resolver);
      if (next == null) {
        return; // level is poisoned: the mapped value is discarded, the level stays null
      }
      current = next;
    }
    current.put(targetPath.getLast(), value);
  }

  /**
   * Returns the map at the given path prefix, creating it if the current entry is absent or a plain
   * value. A newly created map is seeded with the top-level entries of the resolved scope value at
   * that path (children stay opaque buffers); a non-context scope value poisons the level instead.
   * Returns {@code null} when the level is (or becomes) poisoned.
   */
  private @Nullable Map<String, Object> getOrSeedNested(
      final Map<String, Object> parent,
      final List<String> pathPrefix,
      final Function<List<String>, @Nullable DirectBuffer> resolver) {
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
    final var scopeValue = resolver.apply(pathPrefix);
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
