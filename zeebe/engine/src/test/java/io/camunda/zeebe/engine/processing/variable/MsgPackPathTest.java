/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

public final class MsgPackPathTest {

  @Test
  public void shouldReturnDocumentForEmptyPath() {
    final var document = asMsgPack("{'a': 1}");
    final var result = MsgPackPath.navigate(document, List.of(), 0);
    MsgPackUtil.assertEquality(result, "{'a': 1}");
  }

  @Test
  public void shouldNavigateNestedPath() {
    final var document = asMsgPack("{'b': {'c': 42}, 'd': 2}");
    MsgPackUtil.assertEquality(MsgPackPath.navigate(document, List.of("a", "b"), 1), "{'c': 42}");
    MsgPackUtil.assertEquality(MsgPackPath.navigate(document, List.of("a", "b", "c"), 1), "42");
  }

  @Test
  public void shouldReturnNullForMissingKey() {
    final var document = asMsgPack("{'b': 1}");
    assertThat(MsgPackPath.navigate(document, List.of("a", "x"), 1)).isNull();
  }

  @Test
  public void shouldReturnNullWhenIntermediateValueIsNotAMap() {
    final var document = asMsgPack("{'b': 5}");
    assertThat(MsgPackPath.navigate(document, List.of("a", "b", "c"), 1)).isNull();
  }

  @Test
  public void shouldReturnNullWhenDocumentIsNotAMap() {
    final var document = asMsgPack("5");
    assertThat(MsgPackPath.navigate(document, List.of("a", "b"), 1)).isNull();
  }

  @Test
  public void shouldReturnNilValueAtPath() {
    final var document = asMsgPack("{'b': null}");
    MsgPackUtil.assertEquality(MsgPackPath.navigate(document, List.of("a", "b"), 1), "null");
  }

  @Test
  public void shouldSkipPrecedingEntriesToFindKey() {
    final var document = asMsgPack("{'a': 1, 'b': 2}");
    MsgPackUtil.assertEquality(MsgPackPath.navigate(document, List.of("x", "b"), 1), "2");
  }

  @Test
  public void shouldSkipNonStringMapKeys() {
    final var writer = new MsgPackWriter();
    final var buffer = new ExpandableArrayBuffer();
    writer.wrap(buffer, 0);
    // asMsgPack (JSON-based) can't produce a non-string map key, so build the map by hand: an
    // integer key 1 -> 99, followed by a string key "1" -> 42 — same text as the integer key, to
    // prove navigate() matches on key type, not just text
    writer.writeMapHeader(2);
    writer.writeInteger(1);
    writer.writeInteger(99);
    writer.writeString(BufferUtil.wrapString("1"));
    writer.writeInteger(42);
    final var document = new UnsafeBuffer(buffer, 0, writer.getOffset());

    MsgPackUtil.assertEquality(MsgPackPath.navigate(document, List.of("x", "1"), 1), "42");
  }
}
