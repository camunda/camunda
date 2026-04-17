/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.camunda.zeebe.test.util.MsgPackUtil.assertEquality;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import io.camunda.zeebe.msgpack.MsgPackUtil;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link MsgPackUtil#mergeMsgPackDocuments(DirectBuffer, DirectBuffer)}.
 *
 * <p>These tests verify the deep-merge semantics at the MsgPack binary level, independently of FEEL
 * expression evaluation or engine integration. They guard against regressions in:
 *
 * <ul>
 *   <li>Content-based key comparison (ByteBuffer vs UnsafeBuffer equality)
 *   <li>Recursive merging of nested map values
 *   <li>Override-wins semantics for leaf values
 *   <li>Preservation of base-only keys at every nesting level
 * </ul>
 */
class MsgPackDocumentMergeTest {

  static Stream<Arguments> shouldMergeDocuments() {
    return Stream.of(
        // Flat map merging
        Arguments.of(
            Named.of("should return base when override is empty", "{'a':1,'b':2}"),
            "{}",
            "{'a':1,'b':2}"),
        Arguments.of(
            Named.of("should return override when base is empty", "{}"), "{'x':10}", "{'x':10}"),
        Arguments.of(Named.of("should merge disjoint keys", "{'a':1}"), "{'b':2}", "{'a':1,'b':2}"),
        Arguments.of(
            Named.of("should override matching leaf keys", "{'a':1,'b':2}"),
            "{'a':99}",
            "{'a':99,'b':2}"),
        Arguments.of(
            Named.of("should override all matching keys", "{'a':1,'b':2,'c':3}"),
            "{'a':10,'b':20,'c':30}",
            "{'a':10,'b':20,'c':30}"),
        Arguments.of(
            Named.of("should handle string values", "{'name':'Alice','role':'admin'}"),
            "{'name':'Bob'}",
            "{'name':'Bob','role':'admin'}"),

        // One-level nested map merging
        Arguments.of(
            Named.of(
                "should deep merge nested maps preserving base-only keys",
                "{'data':{'a':1,'b':2}}"),
            "{'data':{'c':3}}",
            "{'data':{'a':1,'b':2,'c':3}}"),
        Arguments.of(
            Named.of("should deep merge overriding matching nested keys", "{'data':{'a':1,'b':2}}"),
            "{'data':{'b':99}}",
            "{'data':{'a':1,'b':99}}"),
        Arguments.of(
            Named.of(
                "should preserve sibling map when only one branch is overridden",
                "{'processData':{'salary':{'beneficial':2},'humanTask':{'outcome':'OLD'}}}"),
            "{'processData':{'humanTask':{'outcome':'NEW'}}}",
            "{'processData':{'salary':{'beneficial':2},'humanTask':{'outcome':'NEW'}}}"),

        // Multi-level deep nesting
        Arguments.of(
            Named.of("should deep merge at three levels", "{'a':{'b':{'c':1,'d':2},'e':3}}"),
            "{'a':{'b':{'c':99}}}",
            "{'a':{'b':{'c':99,'d':2},'e':3}}"),
        Arguments.of(
            Named.of(
                "should deep merge at four levels",
                "{'l1':{'l2':{'l3':{'l4_a':'keep','l4_b':'keep'},'l3_sib':'keep'}}}"),
            "{'l1':{'l2':{'l3':{'l4_a':'changed'}}}}",
            "{'l1':{'l2':{'l3':{'l4_a':'changed','l4_b':'keep'},'l3_sib':'keep'}}}"),

        // Mixed value types
        Arguments.of(
            Named.of("should override map with scalar", "{'x':{'nested':1}}"),
            "{'x':42}",
            "{'x':42}"),
        Arguments.of(
            Named.of("should override scalar with map", "{'x':42}"),
            "{'x':{'nested':1}}",
            "{'x':{'nested':1}}"),
        Arguments.of(
            Named.of(
                "should handle array values (replace entirely)", "{'arr':[1,2],'other':'keep'}"),
            "{'arr':[3,4,5]}",
            "{'arr':[3,4,5],'other':'keep'}"),
        Arguments.of(
            Named.of("should handle boolean values", "{'active':false,'name':'test'}"),
            "{'active':true}",
            "{'active':true,'name':'test'}"),
        Arguments.of(
            Named.of("should handle null values", "{'a':1,'b':2}"),
            "{'a':null}",
            "{'a':null,'b':2}"),

        // Edge cases
        Arguments.of(Named.of("should merge two empty documents", "{}"), "{}", "{}"),
        Arguments.of(
            Named.of("should merge identical documents", "{'a':1,'b':{'c':2}}"),
            "{'a':1,'b':{'c':2}}",
            "{'a':1,'b':{'c':2}}"),
        Arguments.of(
            Named.of("should handle nested empty maps", "{'a':{},'b':{'c':1}}"),
            "{'a':{'x':1},'b':{}}",
            "{'a':{'x':1},'b':{'c':1}}"),
        Arguments.of(
            Named.of("should handle override adding new nested branch", "{'existing':{'a':1}}"),
            "{'newBranch':{'x':10}}",
            "{'existing':{'a':1},'newBranch':{'x':10}}"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  void shouldMergeDocuments(final String base, final String overrides, final String expected) {
    // given
    final DirectBuffer baseDoc = asMsgPack(base);
    final DirectBuffer overrideDoc = asMsgPack(overrides);

    // when
    final DirectBuffer merged = MsgPackUtil.mergeMsgPackDocuments(baseDoc, overrideDoc);

    // then
    assertEquality(merged, expected);
  }

  @Nested
  @DisplayName("Large and boundary cases")
  class LargeAndBoundaryCases {

    @Test
    @DisplayName("should handle large number of keys")
    void shouldHandleLargeNumberOfKeys() {
      // given — 20 keys, override changes only one
      final var baseJson = new StringBuilder("{");
      for (int i = 0; i < 20; i++) {
        if (i > 0) {
          baseJson.append(",");
        }
        baseJson.append("\"k").append(i).append("\":").append(i);
      }
      baseJson.append("}");

      final DirectBuffer base = asMsgPack(baseJson.toString());
      final DirectBuffer overrides = asMsgPack("{\"k10\":999}");

      // when
      final DirectBuffer merged = MsgPackUtil.mergeMsgPackDocuments(base, overrides);

      // then
      final var expectedJson = new StringBuilder("{");
      for (int i = 0; i < 20; i++) {
        if (i > 0) {
          expectedJson.append(",");
        }
        expectedJson.append("\"k").append(i).append("\":").append(i == 10 ? 999 : i);
      }
      expectedJson.append("}");
      assertEquality(merged, expectedJson.toString());
    }

    @Test
    @DisplayName("Deep recursion (999 levels) with siblings preserved at every level")
    void shouldDeepMerge999LevelsWithSiblingsPreserved() {
      // given
      final int depth = 999;

      final var baseJson = new StringBuilder("{");
      for (int i = 0; i < depth; i++) {
        if (i > 0) {
          baseJson.append(",");
        }
        baseJson.append("\"l").append(i).append("\":{\"sibling").append(i).append("\":").append(i);
      }
      baseJson.append(",\"leaf\":\"old\"");
      baseJson.append("}".repeat(depth + 1));

      final var overrideJson = new StringBuilder();
      for (int i = 0; i < depth; i++) {
        overrideJson.append("{\"l").append(i).append("\":");
      }
      overrideJson.append("{\"leaf\":\"new\"}");
      overrideJson.append("}".repeat(depth));

      final DirectBuffer base = asMsgPack(baseJson.toString());
      final DirectBuffer overrides = asMsgPack(overrideJson.toString());

      // when
      final DirectBuffer merged = MsgPackUtil.mergeMsgPackDocuments(base, overrides);

      // then
      final var expectedJson = new StringBuilder("{");
      for (int i = 0; i < depth; i++) {
        if (i > 0) {
          expectedJson.append(",");
        }
        expectedJson
            .append("\"l")
            .append(i)
            .append("\":{\"sibling")
            .append(i)
            .append("\":")
            .append(i);
      }
      expectedJson.append(",\"leaf\":\"new\"");
      expectedJson.append("}".repeat(depth + 1));
      assertEquality(merged, expectedJson.toString());
    }

    @Test
    @DisplayName("Deep recursion (1001 levels) throws StreamConstraintsException")
    void shouldDeepMerge1001LevelsThrowsStreamConstraintsException() {
      // given
      final int depth = 1000;

      final var baseJson = new StringBuilder("{");
      for (int i = 0; i < depth; i++) {
        if (i > 0) {
          baseJson.append(",");
        }
        baseJson.append("\"l").append(i).append("\":{\"sibling").append(i).append("\":").append(i);
      }
      baseJson.append(",\"leaf\":\"old\"");
      baseJson.append("}".repeat(depth + 1));

      final String json = baseJson.toString();

      // when / then
      assertThatThrownBy(() -> asMsgPack(json))
          .isInstanceOf(RuntimeException.class)
          .hasCauseInstanceOf(StreamConstraintsException.class);
    }
  }
}
