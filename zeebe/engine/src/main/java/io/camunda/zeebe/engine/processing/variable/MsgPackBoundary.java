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
 * The MessagePack boundary shared by the {@link MappingResultBuilder} implementations: reading a
 * persisted scope value ({@link #propertiesOf}), writing the accumulated document ({@link
 * #toMsgPack}) and detaching transient evaluation buffers ({@link #copyIfMsgPack}). Stateless, so
 * the builders use it rather than extend it.
 */
@NullMarked
final class MsgPackBoundary {

  private MsgPackBoundary() {}

  /**
   * Copies a {@link ContextValue.MsgPack}'s buffer, because evaluation result buffers are transient
   * and may be reused by the next evaluation. A {@link ContextValue.Evaluated} needs no copy: it
   * holds an immutable FEEL value.
   */
  static ContextValue copyIfMsgPack(final ContextValue value) {
    return value instanceof ContextValue.MsgPack(final var buffer)
        ? new ContextValue.MsgPack(BufferUtil.cloneBuffer(buffer))
        : value;
  }

  /**
   * Reads a persisted scope value. Empty unless it is a map: MessagePack is what storage holds a
   * scope value as, so this is the shape any code reading one has to unwrap.
   */
  static Map<String, DirectBuffer> propertiesOf(final @Nullable DirectBuffer scopeValue) {
    if (scopeValue == null || !MsgPackCodes.isMap(scopeValue.getByte(0))) {
      return Map.of();
    }
    final var result = new LinkedHashMap<String, DirectBuffer>();
    final var reader = new MsgPackReader();
    reader.wrap(scopeValue, 0, scopeValue.capacity());
    final int entryCount = reader.readMapHeader();
    for (int i = 0; i < entryCount; i++) {
      final var key = BufferUtil.bufferAsString(reader.readToken().getValueBuffer());
      final int valueOffset = reader.getOffset();
      reader.skipValue();
      final var slice = new UnsafeBuffer(scopeValue, valueOffset, reader.getOffset() - valueOffset);
      result.put(key, BufferUtil.cloneBuffer(slice));
    }
    return result;
  }

  /** Writes the accumulated document. */
  static DirectBuffer toMsgPack(final ContextValue.Structure document) {
    final var writer = new MsgPackWriter();
    final var buffer = new ExpandableArrayBuffer();
    writer.wrap(buffer, 0);
    writeStructure(writer, document);
    return new UnsafeBuffer(buffer, 0, writer.getOffset());
  }

  /**
   * Writes a structure to msgpack using an iterative depth-first traversal instead of recursion. A
   * {@code zeebe:input} target path can have an unbounded number of '.'-separated segments
   * (ZeebeExpressionValidator's path pattern doesn't cap it), and this runs on every element
   * activation — plain recursion here previously let a deeply-nested target throw an uncaught
   * StackOverflowError before NestingDepthValidator ever got a chance to reject the document
   * gracefully.
   */
  private static void writeStructure(
      final MsgPackWriter writer, final ContextValue.Structure root) {
    final Deque<Iterator<Map.Entry<String, ContextValue>>> pending = new ArrayDeque<>();
    writer.writeMapHeader(root.entries().size());
    pending.push(root.entries().entrySet().iterator());

    while (!pending.isEmpty()) {
      final var iterator = pending.peek();
      if (!iterator.hasNext()) {
        pending.pop();
        continue;
      }
      final var entry = iterator.next();
      writer.writeString(BufferUtil.wrapString(entry.getKey()));
      switch (entry.getValue()) {
        case ContextValue.Structure(final var nested) -> {
          writer.writeMapHeader(nested.size());
          pending.push(nested.entrySet().iterator());
        }
        case ContextValue.MsgPack(final var buffer) -> writer.writeRaw(buffer);
        case ContextValue.Evaluated(final var result) ->
            // toBuffer() returns a view over the expression language's shared, reused write
            // buffer; that is safe only because writeRaw copies immediately and nothing runs
            // between the two calls.
            writer.writeRaw(result.toBuffer());
      }
    }
  }
}
