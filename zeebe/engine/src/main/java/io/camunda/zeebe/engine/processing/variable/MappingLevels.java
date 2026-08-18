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
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The msgpack representation shared by {@link InputMappingResultBuilder} and {@link
 * OutputMappingResultBuilder}: an accumulated level and the (optionally layered) serializer.
 * Package-private and mode-agnostic — neither builder's notion of what a level means belongs here,
 * only the structure both of them accumulate.
 */
@NullMarked
final class MappingLevels {

  private MappingLevels() {}

  static boolean isNil(final DirectBuffer value) {
    return value.capacity() == 1 && value.getByte(0) == MsgPackCodes.NIL;
  }

  /** Reads the top level of a msgpack map into a builder map; values stay opaque cloned slices. */
  static Map<String, Object> deserializeTopLevel(final DirectBuffer map) {
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
  static DirectBuffer serialize(
      final Map<String, Object> children, final @Nullable DirectBuffer shadowed) {
    final var writer = new MsgPackWriter();
    final var buffer = new ExpandableArrayBuffer();
    writer.wrap(buffer, 0);
    writeLayered(writer, children, shadowed);
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
  private static void writeLayered(
      final MsgPackWriter writer,
      final Map<String, Object> rootChildren,
      final @Nullable DirectBuffer rootShadowed) {
    final Deque<LevelFrame> pending = new ArrayDeque<>();
    pending.push(openLevel(writer, rootChildren, rootShadowed));

    while (!pending.isEmpty()) {
      final var frame = pending.peek();
      if (!frame.entries().hasNext()) {
        pending.pop();
        continue;
      }
      final var entry = frame.entries().next();
      writer.writeString(BufferUtil.wrapString(entry.getKey()));
      final var value = entry.getValue();
      if (value instanceof final Level level) {
        pending.push(
            openLevel(writer, level.children(), shadowedChildOf(frame, entry.getKey(), level)));
      } else {
        writer.writeRaw((DirectBuffer) value);
      }
    }
  }

  /**
   * Writes the map header for one level and returns the frame to iterate it: the level's own
   * entries written over the top level of the value it shadows.
   */
  private static LevelFrame openLevel(
      final MsgPackWriter writer,
      final Map<String, Object> children,
      final @Nullable DirectBuffer shadowed) {
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
      final LevelFrame frame, final String key, final Level level) {
    if (level.replacedExistingValue()) {
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
   * @param children the level's entries: a nested {@link Level} or a MsgPack {@link DirectBuffer}
   * @param replacedExistingValue whether this level replaced a plain value rather than being
   *     created from nothing — a neutral structural fact both builders compute. {@link
   *     InputMappingResultBuilder} is the only one that acts on it: such a level never falls
   *     through to the value it replaced on a read, because that earlier assignment already
   *     replaced that value outright, and re-creating the level is a fresh start (structural
   *     last-wins) rather than a merge into what it dropped.
   */
  record Level(Map<String, Object> children, boolean replacedExistingValue) {
    static Level fresh(final boolean replacedExistingValue) {
      return new Level(new LinkedHashMap<>(), replacedExistingValue);
    }
  }
}
