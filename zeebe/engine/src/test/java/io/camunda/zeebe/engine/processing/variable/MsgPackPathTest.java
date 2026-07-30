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

import io.camunda.zeebe.test.util.MsgPackUtil;
import java.util.List;
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
}
