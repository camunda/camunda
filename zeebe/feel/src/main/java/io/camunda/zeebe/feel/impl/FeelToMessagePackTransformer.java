/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.feel.impl;

import static io.camunda.zeebe.feel.impl.Loggers.LOGGER;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValBoolean;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValDate;
import org.camunda.feel.syntaxtree.ValDateTime;
import org.camunda.feel.syntaxtree.ValDayTimeDuration;
import org.camunda.feel.syntaxtree.ValList;
import org.camunda.feel.syntaxtree.ValLocalDateTime;
import org.camunda.feel.syntaxtree.ValLocalTime;
import org.camunda.feel.syntaxtree.ValNull$;
import org.camunda.feel.syntaxtree.ValNumber;
import org.camunda.feel.syntaxtree.ValString;
import org.camunda.feel.syntaxtree.ValTime;
import org.camunda.feel.syntaxtree.ValYearMonthDuration;
import scala.Tuple2;
import scala.collection.Iterator;

public class FeelToMessagePackTransformer {
  final MsgPackWriter writer = new MsgPackWriter();
  final MutableDirectBuffer writeBuffer = new ExpandableArrayBuffer();
  final DirectBuffer resultView = new UnsafeBuffer();
  final DirectBuffer stringWrapper = new UnsafeBuffer();

  public DirectBuffer toMessagePack(final Val value) {
    writer.wrap(writeBuffer, 0);
    writeValue(value);

    resultView.wrap(writeBuffer, 0, writer.getOffset());
    return resultView;
  }

  /**
   * Writes a (possibly deeply nested) FEEL value to msgpack using an iterative depth-first search
   * approach. This avoids using the call stack from recursion and instead uses an explicit
   * work-stack to manage nested lists/contexts.
   */
  private void writeValue(final Val root) {
    final Deque<Object> stack = new ArrayDeque<>();
    writeScalarOrPushCursor(root, stack);

    while (!stack.isEmpty()) {
      final var cursor = stack.peek();
      switch (cursor) {
        case final ListCursor listCursor -> {
          if (listCursor.items().hasNext()) {
            writeScalarOrPushCursor(listCursor.items().next(), stack);
          } else {
            stack.pop();
          }
        }
        case final ContextCursor contextCursor -> {
          if (contextCursor.entries().hasNext()) {
            writeContextEntry(contextCursor.entries().next(), stack);
          } else {
            stack.pop();
          }
        }
        default -> throw new IllegalStateException("Unexpected cursor on stack: " + cursor);
      }
    }
  }

  private void writeContextEntry(final Tuple2<String, Object> entry, final Deque<Object> pending) {
    final var entryKey = entry._1();
    final var entryValue = entry._2();
    stringWrapper.wrap(entryKey.getBytes());
    writer.writeString(stringWrapper);

    switch (entryValue) {
      case final Val entryVal -> writeScalarOrPushCursor(entryVal, pending);
      case final DirectBuffer entryBuffer -> writer.writeRaw(entryBuffer);
      default -> {
        writer.writeNil();
        LOGGER.trace(
            "No FEEL to MessagePack transformation for '{}'. Using 'null' for context entry with key '{}'.",
            entryValue,
            entryKey);
      }
    }
  }

  /**
   * Writes a scalar value directly, or writes a list/map header and pushes a cursor onto {@code
   * pending} so its items/entries are processed by the caller's loop instead of recursively.
   */
  private void writeScalarOrPushCursor(final Val value, final Deque<Object> pending) {
    switch (value) {
      case final ValNull$ ignored -> writer.writeNil();
      case final ValNumber number -> {
        if (number.value().isWhole()) {
          writer.writeInteger(number.value().longValue());
        } else {
          writer.writeFloat(number.value().doubleValue());
        }
      }
      case final ValBoolean booleanValue -> writer.writeBoolean(booleanValue.value());
      case final ValString string -> writeStringValue(string.value());
      case final ValList list -> {
        final var items = list.itemsAsSeq();
        writer.writeArrayHeader(items.size());
        pending.push(new ListCursor(items.iterator()));
      }
      case final ValContext context -> {
        if (context.context() instanceof final MessagePackContext msgPackContext) {
          writer.writeRaw(msgPackContext.messagePackMap);
        } else {
          final var variables = context.context().variableProvider().getVariables();
          writer.writeMapHeader(variables.size());
          pending.push(new ContextCursor(variables.iterator()));
        }
      }
      case final ValTime time -> writeStringValue(time.value().format());
      case final ValLocalTime time -> writeStringValue(time.value().toString());
      case final ValDate date -> writeStringValue(date.value().toString());
      case final ValDateTime dateTime ->
          writeStringValue(ISO_ZONED_DATE_TIME.format(dateTime.value()));
      case final ValLocalDateTime dateTime ->
          writeStringValue(ISO_LOCAL_DATE_TIME.format(dateTime.value()));
      case final ValDayTimeDuration duration -> writeStringValue(duration.toString());
      case final ValYearMonthDuration duration -> writeStringValue(duration.toString());
      default -> {
        writer.writeNil();
        LOGGER.trace(
            "No FEEL to MessagePack transformation for '{}'. Using 'null' instead.", value);
      }
    }
  }

  private void writeStringValue(final String value) {
    stringWrapper.wrap(value.getBytes());
    writer.writeString(stringWrapper);
  }

  /** Cursor over a {@link ValList}'s pending, not-yet-written items. */
  private record ListCursor(Iterator<Val> items) {}

  /** Cursor over a {@link ValContext}'s pending, not-yet-written entries. */
  private record ContextCursor(Iterator<Tuple2<String, Object>> entries) {}
}
