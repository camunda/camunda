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
 * <p>For input mappings ({@link #forInputMappings}), a mapping to a nested target descends into (or
 * creates) intermediate objects; a mapping whose target collides structurally with an earlier one
 * (a value where an object is needed, or vice versa) replaces the earlier entry — last-wins.
 *
 * <p>For output mappings ({@link #forOutputMappings}), a nested target instead merges with the
 * existing scope variable at every path level: a level that is absent or holds a plain value is
 * seeded from the current scope value at that path. A non-context scope value poisons the level to
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
   * Values are either a nested {@link NestedLevel}, {@link #POISON}, or a MsgPack {@link
   * DirectBuffer}.
   */
  private final Map<String, Object> entries = new LinkedHashMap<>();

  private final MsgPackWriter writer = new MsgPackWriter();

  /**
   * Output mappings only: resolves a target-path prefix to the scope value to seed a level from.
   */
  private final Function<List<String>, @Nullable DirectBuffer> seedValueResolver;

  /**
   * Input mappings only: resolves a top-level variable name to the value the element's scope chain
   * gives it — the value a nested target partially shadows.
   */
  private final Function<String, @Nullable DirectBuffer> shadowedValueResolver;

  private MappingResultBuilder(
      final Function<List<String>, @Nullable DirectBuffer> seedValueResolver,
      final Function<String, @Nullable DirectBuffer> shadowedValueResolver) {
    this.seedValueResolver = seedValueResolver;
    this.shadowedValueResolver = shadowedValueResolver;
  }

  /**
   * Builder for input mappings: a nested target never merges with the existing scope value while
   * accumulating.
   *
   * @param shadowedValueResolver resolves a top-level variable name to the value the scope chain
   *     gives it, or {@code null} when there is none
   */
  public static MappingResultBuilder forInputMappings(
      final Function<String, @Nullable DirectBuffer> shadowedValueResolver) {
    return new MappingResultBuilder(path -> null, shadowedValueResolver);
  }

  /**
   * Builder for output mappings: when a nested target path needs a map at a level that is absent
   * (or currently holds a plain value), the level is seeded from the existing scope value at that
   * path. A non-context scope value poisons the level to null, matching FEEL's {@code context
   * merge(<non-context>, {...})} behavior.
   *
   * @param seedValueResolver resolves a target-path prefix to the current scope value at that path,
   *     or {@code null} when there is none; invoked only when a level must be (re)created
   */
  public static MappingResultBuilder forOutputMappings(
      final Function<List<String>, @Nullable DirectBuffer> seedValueResolver) {
    return new MappingResultBuilder(seedValueResolver, name -> null);
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
      current = next.children();
    }
    current.put(targetPath.getLast(), BufferUtil.cloneBuffer(value));
  }

  /**
   * Returns the accumulated value of the given top-level variable, or {@code null} if no mapping
   * has produced it yet. A nested structure is serialized on lookup, layered over the value its
   * name resolves to in the scope chain (input mappings only) unless an earlier mapping assigned
   * that name whole. A poisoned top-level entry returns a NIL buffer (not Java {@code null}, which
   * would let the caller fall back to the scope lookup instead of seeing the poisoned null).
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
      final var level = (NestedLevel) entry;
      final var shadowed = level.totallyShadowed() ? null : shadowedValueResolver.apply(name);
      return serializeLayered(level.children(), shadowed);
    }
  }

  /** Returns all accumulated results as a single MsgPack document (a map). */
  public DirectBuffer toDocument() {
    return serializeLayered(entries, null);
  }

  /**
   * Returns the level at the given path prefix, creating it if the current entry is absent or a
   * plain value. A newly created level is seeded with the top-level entries of the existing scope
   * value at that path (children stay opaque buffers); a non-context scope value poisons the level
   * instead. Returns {@code null} when the level is (or becomes) poisoned.
   */
  private @Nullable NestedLevel getOrSeedNested(
      final Map<String, Object> parent, final List<String> pathPrefix) {
    final var key = pathPrefix.getLast();
    final var entry = parent.get(key);
    if (entry == POISON) {
      return null;
    }
    if (entry instanceof final NestedLevel level) {
      return level;
    }
    // Absent, or a plain value from an earlier mapping. A plain value means that mapping already
    // replaced whatever this path resolved to, so the re-created level must not fall through to it.
    final var totallyShadowed = entry != null;
    final var scopeValue = seedValueResolver.apply(pathPrefix);
    if (scopeValue == null || isNil(scopeValue)) {
      final var fresh = NestedLevel.fresh(totallyShadowed);
      parent.put(key, fresh);
      return fresh;
    }
    if (!MsgPackCodes.isMap(scopeValue.getByte(0))) {
      parent.put(key, POISON);
      return null;
    }
    final var seeded = new NestedLevel(deserializeTopLevel(scopeValue), totallyShadowed);
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

  /**
   * Serializes a (possibly deeply nested) level, layering it over the given shadowed value. Pass
   * {@code null} to serialize the level as-is.
   */
  private DirectBuffer serializeLayered(
      final Map<String, Object> children, final @Nullable DirectBuffer shadowed) {
    final var buffer = new ExpandableArrayBuffer();
    writer.wrap(buffer, 0);
    writeLayered(children, shadowed);
    return new UnsafeBuffer(buffer, 0, writer.getOffset());
  }

  /**
   * Writes a level to msgpack using an iterative depth-first traversal instead of recursion. A
   * {@code zeebe:input} target path can have an unbounded number of '.'-separated segments
   * (ZeebeExpressionValidator's path pattern doesn't cap it), and this runs on every element
   * activation — plain recursion here previously let a deeply-nested target throw an uncaught
   * StackOverflowError before NestingDepthValidator ever got a chance to reject the document
   * gracefully.
   *
   * <p>At each level the accumulated entries are written over the top level of the value that level
   * shadows, so a key no mapping defined resolves to the shadowed value's key. A level that an
   * earlier mapping assigned whole shadows totally and stops the fall-through for its whole
   * subtree.
   */
  private void writeLayered(
      final Map<String, Object> rootChildren, final @Nullable DirectBuffer rootShadowed) {
    final Deque<LevelFrame> pending = new ArrayDeque<>();
    pending.push(openLevel(rootChildren, rootShadowed));

    while (!pending.isEmpty()) {
      final var frame = pending.peek();
      if (!frame.entries().hasNext()) {
        pending.pop();
        continue;
      }
      final var entry = frame.entries().next();
      writer.writeString(BufferUtil.wrapString(entry.getKey()));
      final var value = entry.getValue();
      if (value instanceof final NestedLevel level) {
        pending.push(openLevel(level.children(), shadowedChildOf(frame, entry.getKey(), level)));
      } else if (value == POISON) {
        writer.writeNil();
      } else {
        writer.writeRaw((DirectBuffer) value);
      }
    }
  }

  /**
   * Writes the map header for one level and returns the frame to iterate it: the level's own
   * entries written over the top level of the value it shadows.
   */
  private LevelFrame openLevel(
      final Map<String, Object> children, final @Nullable DirectBuffer shadowed) {
    final Map<String, Object> shadowedChildren =
        shadowed != null && !isNil(shadowed) && MsgPackCodes.isMap(shadowed.getByte(0))
            ? deserializeTopLevel(shadowed)
            : Map.of();
    final Map<String, Object> merged;
    if (shadowedChildren.isEmpty()) {
      merged = children;
    } else {
      // a fresh map: the accumulated level must not gain the shadowed value's keys
      merged = new LinkedHashMap<>(shadowedChildren);
      merged.putAll(children);
    }
    writer.writeMapHeader(merged.size());
    return new LevelFrame(merged.entrySet().iterator(), shadowedChildren);
  }

  private static @Nullable DirectBuffer shadowedChildOf(
      final LevelFrame frame, final String key, final NestedLevel level) {
    if (level.totallyShadowed()) {
      return null;
    }
    return frame.shadowedChildren().get(key) instanceof final DirectBuffer shadowed
        ? shadowed
        : null;
  }

  /** One level of the iterative traversal: the entries to write, and what they shadow. */
  private record LevelFrame(
      Iterator<Map.Entry<String, Object>> entries, Map<String, Object> shadowedChildren) {}

  /**
   * A nested level built by one or more dotted target paths.
   *
   * @param children the level's entries: a nested {@link NestedLevel}, {@link #POISON}, or a
   *     MsgPack {@link DirectBuffer}
   * @param totallyShadowed whether an earlier mapping assigned this whole path a value before this
   *     level was created. Such a level never falls through to the value it shadows on a read: the
   *     earlier assignment already replaced that value outright, and re-creating the level is a
   *     fresh start (structural last-wins) rather than a merge into what it dropped.
   */
  private record NestedLevel(Map<String, Object> children, boolean totallyShadowed) {
    static NestedLevel fresh(final boolean totallyShadowed) {
      return new NestedLevel(new LinkedHashMap<>(), totallyShadowed);
    }
  }
}
