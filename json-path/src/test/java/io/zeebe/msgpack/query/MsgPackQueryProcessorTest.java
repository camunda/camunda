/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.query;

import static io.zeebe.msgpack.util.MsgPackUtil.encodeMsgPack;
import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.ArrayResult;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResult;
import io.zeebe.msgpack.query.MsgPackQueryProcessor.QueryResults;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
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
  public void shouldGetSingleResultArray() {
    final QueryResults results =
        processor.process(
            path("foo"),
            encodeMsgPack(
                p -> {
                  p.packMapHeader(1).packString("foo").packArrayHeader(5);
                  p.packString("bar");
                  p.packLong(123L);
                  p.packBoolean(true);
                  p.packNil();
                  p.packMapHeader(2).packString("a").packLong(1).packString("b").packLong(2);
                }));

    assertThat(results.size()).isEqualTo(1);

    final QueryResult result = results.getSingleResult();
    assertThat(result).isNotNull();
    assertThat(result.isArray()).isTrue();

    final ArrayResult arrayResult = result.getArray();
    assertThat(arrayResult.size()).isEqualTo(5);
  }

  @Test
  public void shouldIterateOverResultArray() {
    final QueryResults results =
        processor.process(
            path("foo"),
            encodeMsgPack(
                p -> {
                  p.packMapHeader(1).packString("foo").packArrayHeader(5);
                  p.packString("bar");
                  p.packLong(123L);
                  p.packBoolean(true);
                  p.packNil();
                  p.packMapHeader(2).packString("a").packLong(1).packString("b").packLong(2);
                }));

    final ArrayResult arrayResult = results.getSingleResult().getArray();

    final List<DirectBuffer> array = new ArrayList<>();
    arrayResult.forEach(element -> array.add(cloneBuffer(element)));

    assertThat(array)
        .hasSize(5)
        .containsExactly(
            encodeMsgPack(p -> p.packString("bar")),
            encodeMsgPack(p -> p.packLong(123L)),
            encodeMsgPack(p -> p.packBoolean(true)),
            encodeMsgPack(p -> p.packNil()),
            encodeMsgPack(
                p -> p.packMapHeader(2).packString("a").packLong(1).packString("b").packLong(2)));
  }

  @Test
  public void shouldGetElementOfArrayResult() {
    final QueryResults results =
        processor.process(
            path("foo"),
            encodeMsgPack(
                p -> {
                  p.packMapHeader(1).packString("foo").packArrayHeader(5);
                  p.packString("bar");
                  p.packLong(123L);
                  p.packBoolean(true);
                  p.packNil();
                  p.packMapHeader(2).packString("a").packLong(1).packString("b").packLong(2);
                }));

    final ArrayResult arrayResult = results.getSingleResult().getArray();

    assertThat(arrayResult.getElement(0)).isEqualTo(encodeMsgPack(p -> p.packString("bar")));
    assertThat(arrayResult.getElement(1)).isEqualTo(encodeMsgPack(p -> p.packLong(123L)));
    assertThat(arrayResult.getElement(2)).isEqualTo(encodeMsgPack(p -> p.packBoolean(true)));
    assertThat(arrayResult.getElement(3)).isEqualTo(encodeMsgPack(p -> p.packNil()));
    assertThat(arrayResult.getElement(4))
        .isEqualTo(
            encodeMsgPack(
                p -> p.packMapHeader(2).packString("a").packLong(1).packString("b").packLong(2)));
  }

  @Test
  public void shouldGetSingleResultValue() {
    final var data =
        encodeMsgPack(
            p -> {
              p.packMapHeader(6);
              p.packString("string").packString("foo");
              p.packString("integer").packLong(123);
              p.packString("boolean").packBoolean(true);
              p.packString("nil").packNil();
              p.packString("array").packArrayHeader(1).packString("x");
              p.packString("map").packMapHeader(1).packString("y").packString("z");
            });

    final Function<String, DirectBuffer> getValue =
        entry -> processor.process(path(entry), data).getSingleResult().getValue();

    assertThat(getValue.apply("string")).isEqualTo(encodeMsgPack(p -> p.packString("foo")));
    assertThat(getValue.apply("integer")).isEqualTo(encodeMsgPack(p -> p.packLong(123)));
    assertThat(getValue.apply("boolean")).isEqualTo(encodeMsgPack(p -> p.packBoolean(true)));
    assertThat(getValue.apply("nil")).isEqualTo(encodeMsgPack(p -> p.packNil()));
    assertThat(getValue.apply("array"))
        .isEqualTo(encodeMsgPack(p -> p.packArrayHeader(1).packString("x")));
    assertThat(getValue.apply("map"))
        .isEqualTo(encodeMsgPack(p -> p.packMapHeader(1).packString("y").packString("z")));
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
        .hasMessage("expected STRING but found 'BOOLEAN'");
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
        .hasMessage("expected LONG but found 'BOOLEAN'");
  }

  @Test
  public void shouldThrowExceptionIfNotArray() {
    final QueryResults results =
        processor.process(
            path("foo"),
            encodeMsgPack(
                p -> {
                  p.packMapHeader(1);
                  p.packString("foo").packString("bar");
                }));

    assertThat(results.size()).isEqualTo(1);

    assertThatThrownBy(() -> results.getSingleResult().getArray())
        .isInstanceOf(RuntimeException.class)
        .hasMessage("expected ARRAY but found 'STRING'");
  }

  @Test
  public void shouldThrowExceptionIfIndexOutOfArray() {
    final QueryResults results =
        processor.process(
            path("foo"),
            encodeMsgPack(
                p -> {
                  p.packMapHeader(1).packString("foo").packArrayHeader(1);
                  p.packString("bar");
                }));

    final ArrayResult arrayResult = results.getSingleResult().getArray();

    assertThatThrownBy(() -> arrayResult.getElement(1))
        .isInstanceOf(IndexOutOfBoundsException.class)
        .hasMessage("index: 1, size: 1");
  }

  @Test
  public void shouldThrowExceptionIfIndexOutOfOrder() {
    final QueryResults results =
        processor.process(
            path("foo"),
            encodeMsgPack(
                p -> {
                  p.packMapHeader(1).packString("foo").packArrayHeader(2);
                  p.packString("bar");
                  p.packString("baz");
                }));

    final ArrayResult arrayResult = results.getSingleResult().getArray();

    arrayResult.getElement(1);

    assertThatThrownBy(() -> arrayResult.getElement(0))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("index: 0, current index: 1");
  }

  @Test
  public void shouldGetResultType() {
    final DirectBuffer data =
        encodeMsgPack(
            p -> {
              p.packMapHeader(6);
              p.packString("string").packString("foo");
              p.packString("integer").packLong(123);
              p.packString("boolean").packBoolean(true);
              p.packString("nil").packNil();
              p.packString("array").packArrayHeader(1).packString("x");
              p.packString("map").packMapHeader(1).packString("y").packString("z");
            });

    assertThat(
            Stream.of("string", "integer", "boolean", "nil", "array", "map")
                .map(p -> processor.process(path(p), data))
                .map(QueryResults::getSingleResult)
                .map(QueryResult::getType))
        .containsExactly("STRING", "INTEGER", "BOOLEAN", "NIL", "ARRAY", "MAP");
  }

  private JsonPathQuery path(final String path) {
    return new JsonPathQueryCompiler().compile(path);
  }
}
