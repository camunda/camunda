/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.query;

import static io.zeebe.msgpack.util.MsgPackUtil.encodeMsgPack;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResult;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResults;
import org.agrona.DirectBuffer;
import org.junit.Test;

public class MsgPackQueryProcessorTest {

  private static final DirectBuffer EMPTY_DOCUMENT = encodeMsgPack(p -> p.packMapHeader(0));

  private final MsgPackQueryProcessor processor = new MsgPackQueryProcessor();

  @Test
  public void shouldBeEmpty() {
    final QueryResults results = processor.process(path("foo"), EMPTY_DOCUMENT);

    assertThat(results.size()).isEqualTo(0);
  }

  @Test
  public void shouldGetSingleResultString() {
    final QueryResults results =
        processor.process(
            path("foo"),
            encodeMsgPack(
                p -> {
                  p.packMapHeader(1);
                  p.packString("foo").packString("bar");
                }));

    assertThat(results.size()).isEqualTo(1);

    final QueryResult result = results.getSingleResult();
    assertThat(result).isNotNull();
    assertThat(result.isString()).isTrue();
    assertThat(result.getString()).isEqualTo(wrapString("bar"));
  }

  @Test
  public void shouldGetSingleResultLongAsString() {
    final QueryResults results =
        processor.process(
            path("foo"),
            encodeMsgPack(
                p -> {
                  p.packMapHeader(1);
                  p.packString("foo").packLong(1L);
                }));

    assertThat(results.size()).isEqualTo(1);

    final QueryResult result = results.getSingleResult();
    assertThat(result).isNotNull();
    assertThat(result.isLong()).isTrue();

    assertThat(result.getLongAsString()).isEqualTo(wrapString(String.valueOf(1L)));
  }

  @Test
  public void shouldThrowExceptionIfEmpty() {
    final QueryResults results = processor.process(path("foo"), EMPTY_DOCUMENT);

    assertThatThrownBy(() -> results.getSingleResult())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("no result found");
  }

  @Test
  public void shouldThrowExceptionIfNotString() {
    final QueryResults results =
        processor.process(
            path("foo"),
            encodeMsgPack(
                p -> {
                  p.packMapHeader(1);
                  p.packString("foo").packBoolean(false);
                }));

    assertThat(results.size()).isEqualTo(1);

    assertThatThrownBy(() -> results.getSingleResult().getString())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("expected String but found 'BOOLEAN'");
  }

  @Test
  public void shouldThrowExceptionIfNotLong() {
    final QueryResults results =
        processor.process(
            path("foo"),
            encodeMsgPack(
                p -> {
                  p.packMapHeader(1);
                  p.packString("foo").packBoolean(false);
                }));

    assertThat(results.size()).isEqualTo(1);

    assertThatThrownBy(() -> results.getSingleResult().getLongAsString())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("expected Long but found 'BOOLEAN'");
  }

  private JsonPathQuery path(String path) {
    return new JsonPathQueryCompiler().compile(path);
  }
}
