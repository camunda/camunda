/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.el.ContextValue;
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
 * OutputMappingResultBuilder}: an accumulated level, its snapshot read, and the writer. Package-
 * private and mode-agnostic — neither builder's notion of what a level means belongs here, only the
 * structure both of them accumulate.
 */
@NullMarked
final class MappingLevels {

  private MappingLevels() {}

  static boolean isNil(final DirectBuffer value) {
    return value.capacity() == 1 && value.getByte(0) == MsgPackCodes.NIL;
  }

  /** Reads the top level of a msgpack map; values stay opaque cloned slices. */
  static Map<String, DirectBuffer> deserializeTopLevel(final DirectBuffer map) {
    final var result = new LinkedHashMap<String, DirectBuffer>();
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
   * The top level of a msgpack map as accumulated level entries, for seeding a level from scope.
   */
  static Map<String, Object> seedFrom(final DirectBuffer map) {
    final Map<String, Object> seeded = new LinkedHashMap<>();
    deserializeTopLevel(map)
        .forEach((key, value) -> seeded.put(key, new ContextValue.MsgPack(value)));
    return seeded;
  }

  /**
   * Materialises a level as a snapshot: the level's own entries layered over the top level of the
   * value it shadows, so a key no mapping defined resolves to the shadowed value's key. A level an
   * earlier mapping assigned whole shadows totally and stops the fall-through for its whole
   * subtree.
   *
   * <p>The copy is deep, and it has to be: a later mapping may still write into this level, and a
   * snapshot an earlier mapping already read must not see that write. It is iterative for the same
   * reason the serializer is — a {@code zeebe:input} target path can have an unbounded number of
   * '.'-separated segments (ZeebeExpressionValidator's path pattern doesn't cap it), and plain
   * recursion here previously let a deeply-nested target throw an uncaught StackOverflowError
   * before NestingDepthValidator ever got a chance to reject the document gracefully.
   */
  static ContextValue.Structure materialize(
      final Map<String, Object> rootChildren, final @Nullable DirectBuffer rootShadowed) {
    final Map<String, ContextValue> rootEntries = new LinkedHashMap<>();
    final Deque<Frame> pending = new ArrayDeque<>();
    pending.push(new Frame(rootChildren, rootShadowed, rootEntries));

    while (!pending.isEmpty()) {
      final var frame = pending.pop();
      final var shadowedChildren = topLevelOf(frame.shadowed());
      // the shadowed value's keys first: a key the level also defines then overwrites it in place,
      // keeping the shadowed key's position, exactly as the layered serializer did
      shadowedChildren.forEach(
          (key, value) -> frame.entries().put(key, new ContextValue.MsgPack(value)));

      for (final var entry : frame.children().entrySet()) {
        final var key = entry.getKey();
        if (entry.getValue() instanceof final Level level) {
          final Map<String, ContextValue> nested = new LinkedHashMap<>();
          frame.entries().put(key, new ContextValue.Structure(nested));
          pending.push(
              new Frame(
                  level.children(),
                  level.replacedExistingValue() ? null : shadowedChildren.get(key),
                  nested));
        } else {
          frame.entries().put(key, (ContextValue) entry.getValue());
        }
      }
    }
    return new ContextValue.Structure(rootEntries);
  }

  private static Map<String, DirectBuffer> topLevelOf(final @Nullable DirectBuffer shadowed) {
    return shadowed != null && !isNil(shadowed) && MsgPackCodes.isMap(shadowed.getByte(0))
        ? deserializeTopLevel(shadowed)
        : Map.of();
  }

  /** One level of the iterative traversal: what to copy, what it shadows, where it lands. */
  private record Frame(
      Map<String, Object> children,
      @Nullable DirectBuffer shadowed,
      Map<String, ContextValue> entries) {}

  /** Serializes a (possibly deeply nested) level to msgpack. */
  static DirectBuffer serialize(final Map<String, Object> children) {
    final var writer = new MsgPackWriter();
    final var buffer = new ExpandableArrayBuffer();
    writer.wrap(buffer, 0);
    write(writer, children);
    return new UnsafeBuffer(buffer, 0, writer.getOffset());
  }

  /**
   * Writes a level to msgpack using an iterative depth-first traversal instead of recursion. A
   * {@code zeebe:input} target path can have an unbounded number of '.'-separated segments
   * (ZeebeExpressionValidator's path pattern doesn't cap it), and this runs on every element
   * activation — plain recursion here previously let a deeply-nested target throw an uncaught
   * StackOverflowError before NestingDepthValidator ever got a chance to reject the document
   * gracefully.
   */
  private static void write(final MsgPackWriter writer, final Map<String, Object> rootChildren) {
    final Deque<Iterator<Map.Entry<String, Object>>> pending = new ArrayDeque<>();
    writer.writeMapHeader(rootChildren.size());
    pending.push(rootChildren.entrySet().iterator());

    while (!pending.isEmpty()) {
      final var entries = pending.peek();
      if (!entries.hasNext()) {
        pending.pop();
        continue;
      }
      final var entry = entries.next();
      writer.writeString(BufferUtil.wrapString(entry.getKey()));
      switch (entry.getValue()) {
        case final Level level -> {
          writer.writeMapHeader(level.children().size());
          pending.push(level.children().entrySet().iterator());
        }
        case ContextValue.MsgPack(final var buffer) -> writer.writeRaw(buffer);
        case ContextValue.Evaluated(final var result) ->
            // toBuffer() returns a view over the expression language's shared, reused write
            // buffer; that is safe only because writeRaw copies immediately and nothing runs
            // between the two calls.
            writer.writeRaw(result.toBuffer());
        default ->
            throw new IllegalStateException(
                "Unexpected accumulated mapping value: " + entry.getValue());
      }
    }
  }

  /** Copies a {@link ContextValue.MsgPack}'s buffer; any other value is already immutable. */
  static ContextValue copyIfMsgPack(final ContextValue value) {
    return value instanceof ContextValue.MsgPack(final var buffer)
        ? new ContextValue.MsgPack(BufferUtil.cloneBuffer(buffer))
        : value;
  }

  /**
   * A nested level built by one or more dotted target paths.
   *
   * @param children the level's entries: a nested {@link Level} or a {@link ContextValue}
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
