/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.feel.impl;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.feel.context.CustomContext;
import org.camunda.feel.context.VariableProvider;
import scala.Option;
import scala.Tuple2;
import scala.collection.Iterable;
import scala.collection.immutable.Map;

public class MessagePackContext extends CustomContext {

  public final DirectBuffer messagePackMap;
  private final VariableProvider variableProvider;

  public MessagePackContext(final MsgPackReader reader, final int bufferOffset, final int size) {
    final var valueSpans = readValueSpans(reader, bufferOffset, size);
    messagePackMap =
        cloneBuffer(reader.getBuffer(), bufferOffset, reader.getOffset() - bufferOffset);
    variableProvider = new MessagePackMapVariableProvider(messagePackMap, valueSpans);
  }

  @Override
  public VariableProvider variableProvider() {
    return variableProvider;
  }

  private Map<String, Span> readValueSpans(
      final MsgPackReader reader, final int bufferOffset, final int size) {
    final var spans = Map.<String, Span>newBuilder();
    spans.sizeHint(size);

    for (int i = 0; i < size; i++) {
      final var keyToken = reader.readToken();
      final var keyBuffer = keyToken.getValueBuffer();
      final var key = bufferAsString(keyBuffer);

      final var valueOffset = reader.getOffset();
      reader.skipValue();
      final var valueLength = reader.getOffset() - valueOffset;
      spans.addOne(new Tuple2<>(key, new Span(valueOffset - bufferOffset, valueLength)));
    }
    return spans.result();
  }

  record Span(int offset, int length) {}

  private static final class MessagePackMapVariableProvider implements VariableProvider {
    private final DirectBuffer entries;
    private final DirectBuffer resultView = new UnsafeBuffer();
    private final Map<String, Span> valueSpans;

    private MessagePackMapVariableProvider(
        final DirectBuffer entries, final Map<String, Span> valueSpans) {
      this.entries = entries;
      this.valueSpans = valueSpans;
    }

    @Override
    public Option<Object> getVariable(final String name) {
      return valueSpans
          .get(name)
          .map(
              span -> {
                resultView.wrap(entries, span.offset(), span.length());
                return resultView;
              });
    }

    @Override
    public Iterable<String> keys() {
      return valueSpans.keySet();
    }

    @Override
    public Map<String, Object> getVariables() {
      final var clonedMap = Map.<String, Object>newBuilder();
      valueSpans.foreach(
          entry ->
              clonedMap.addOne(
                  new Tuple2<>(
                      entry._1(), cloneBuffer(entries, entry._2().offset(), entry._2().length()))));
      return clonedMap.result();
    }
  }
}
