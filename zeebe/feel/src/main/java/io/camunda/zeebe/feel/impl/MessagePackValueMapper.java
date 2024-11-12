/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.feel.impl;

import static io.camunda.zeebe.feel.impl.Loggers.LOGGER;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.msgpack.spec.MsgPackToken;
import java.math.BigDecimal;
import java.util.ArrayList;
import org.agrona.DirectBuffer;
import org.camunda.feel.impl.JavaValueMapper;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValBoolean;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValList;
import org.camunda.feel.syntaxtree.ValNull$;
import org.camunda.feel.syntaxtree.ValNumber;
import org.camunda.feel.syntaxtree.ValString;
import scala.Function1;
import scala.Option;
import scala.jdk.javaapi.CollectionConverters;

public final class MessagePackValueMapper extends JavaValueMapper {
  private final MsgPackReader msgPackReader = new MsgPackReader();

  private Val readNext() {
    final var offset = msgPackReader.getOffset();
    final var token = msgPackReader.readToken();
    return read(token, offset);
  }

  private Val read(final MsgPackToken token, final int offset) {
    return switch (token.getType()) {
      case NIL -> ValNull$.MODULE$;
      case INTEGER ->
          new ValNumber(new scala.math.BigDecimal(new BigDecimal(token.getIntegerValue())));
      case BOOLEAN -> new ValBoolean(token.getBooleanValue());
      case FLOAT ->
          new ValNumber(new scala.math.BigDecimal(BigDecimal.valueOf(token.getFloatValue())));
      case ARRAY -> {
        final var size = token.getSize();
        final var items = new ArrayList<Val>(size);
        for (int i = 0; i < size; i++) {
          items.add(readNext());
        }
        yield new ValList(CollectionConverters.asScala(items).toList());
      }
      case MAP -> new ValContext(new MessagePackContext(msgPackReader, offset, token.getSize()));
      case STRING -> new ValString(bufferAsString(token.getValueBuffer()));
      default -> {
        LOGGER.warn(
            "No MessagePack to FEEL transformation for type '{}'. Using 'null' instead.",
            token.getType());
        yield ValNull$.MODULE$;
      }
    };
  }

  @Override
  public Option<Object> unpackVal(final Val value, final Function1<Val, Object> innerValueMapper) {
    // return the value as it is to not lose the type information
    return Option.apply(value);
  }

  @Override
  public Option<Val> toVal(final Object x, final Function1<Object, Val> innerValueMapper) {
    if (x instanceof final DirectBuffer buffer) {
      msgPackReader.wrap(buffer, 0, buffer.capacity());
      return Option.apply(readNext());
    } else {
      return Option.empty();
    }
  }
}
