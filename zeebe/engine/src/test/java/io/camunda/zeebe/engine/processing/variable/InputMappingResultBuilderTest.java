/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static io.camunda.zeebe.test.util.MsgPackUtil.assertEquality;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.el.ContextValue;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class InputMappingResultBuilderTest {

  private final InputMappingResultBuilder builder = new InputMappingResultBuilder(name -> null);

  @Test
  void shouldBuildEmptyDocument() {
    assertEquality(builder.toDocument(), "{}");
  }

  @Test
  void shouldPutTopLevelValue() {
    // when
    builder.put(List.of("x"), msgPack("1"));

    // then
    assertEquality(builder.toDocument(), "{'x': 1}");
  }

  @Test
  void shouldPutNestedValue() {
    // when
    builder.put(List.of("a", "b", "c"), msgPack("1"));

    // then
    assertEquality(builder.toDocument(), "{'a': {'b': {'c': 1}}}");
  }

  @Test
  void shouldMergeSiblingNestedTargets() {
    // when
    builder.put(List.of("a", "b"), msgPack("1"));
    builder.put(List.of("a", "c"), msgPack("2"));

    // then
    assertEquality(builder.toDocument(), "{'a': {'b': 1, 'c': 2}}");
  }

  @Test
  void shouldOverrideDuplicateTarget() {
    // when
    builder.put(List.of("x"), msgPack("1"));
    builder.put(List.of("x"), msgPack("2"));

    // then
    assertEquality(builder.toDocument(), "{'x': 2}");
  }

  @Test
  void shouldReplaceValueWithNestedObject() {
    // given: 'a' first mapped as a plain value, then a nested target descends into it
    builder.put(List.of("a"), msgPack("1"));

    // when
    builder.put(List.of("a", "b"), msgPack("2"));

    // then: structural last-wins
    assertEquality(builder.toDocument(), "{'a': {'b': 2}}");
  }

  @Test
  void shouldReplaceNestedObjectWithValue() {
    // given
    builder.put(List.of("a", "b"), msgPack("1"));

    // when
    builder.put(List.of("a"), msgPack("2"));

    // then
    assertEquality(builder.toDocument(), "{'a': 2}");
  }

  @Test
  void shouldGetTopLevelVariable() {
    // given
    builder.put(List.of("x"), msgPack("1"));

    // when + then
    assertEquality(((ContextValue.MsgPack) builder.getVariable("x")).buffer(), "1");
  }

  @Test
  void shouldGetNestedStructureAsVariable() {
    // given
    builder.put(List.of("a", "b"), msgPack("1"));

    // when + then
    assertEquality(
        MappingResultBuilder.toMsgPack((ContextValue.Structure) builder.getVariable("a")),
        "{'b': 1}");
  }

  @Test
  void shouldReturnNullForUnknownVariable() {
    assertThat(builder.getVariable("unknown")).isNull();
  }

  @Test
  void shouldCopyTheValueBuffer() {
    // given: evaluation result buffers are transient and may be reused by the next evaluation
    final var value = asMsgPack("1");
    builder.put(List.of("x"), new ContextValue.MsgPack(value));

    // when: the source buffer is overwritten after the put
    value.wrap(asMsgPack("2"));

    // then
    assertEquality(builder.toDocument(), "{'x': 1}");
  }

  @Test
  void shouldNotThrowStackOverflowOnDeeplyNestedTarget() {
    // given — a target path with 50 000 segments: validates that the iterative serialization
    // handles extreme input
    final var targetPath =
        IntStream.range(0, 50_000).mapToObj(i -> "a").collect(Collectors.toList());
    builder.put(targetPath, msgPack("1"));

    // when / then — must not throw
    assertThatCode(builder::toDocument).doesNotThrowAnyException();
  }

  @Test
  void shouldLayerNestedLevelOverShadowedValue() {
    // given: the scope chain resolves x to {'a': 1, 'b': 2}
    final var builder =
        new InputMappingResultBuilder(
            name -> name.equals("x") ? asMsgPack("{'a': 1, 'b': 2}") : null);

    // when: only x.a is mapped
    builder.put(List.of("x", "a"), msgPack("3"));

    // then: the read layers the mapped key over the shadowed value ...
    assertEquality(
        MappingResultBuilder.toMsgPack((ContextValue.Structure) builder.getVariable("x")),
        "{'a': 3, 'b': 2}");
    // ... but the document keeps only what was mapped
    assertEquality(builder.toDocument(), "{'x': {'a': 3}}");
  }

  @Test
  void shouldLayerEveryLevelOfANestedLevelOverShadowedValue() {
    // given: a shadowed value nested as deeply as the target path
    final var builder =
        new InputMappingResultBuilder(
            name -> name.equals("x") ? asMsgPack("{'a': {'b': 1, 'c': 2}, 'd': 4}") : null);

    // when
    builder.put(List.of("x", "a", "b"), msgPack("9"));

    // then: unmapped keys survive at every level, which a top-level-only merge would not do
    assertEquality(
        MappingResultBuilder.toMsgPack((ContextValue.Structure) builder.getVariable("x")),
        "{'a': {'b': 9, 'c': 2}, 'd': 4}");
    assertEquality(builder.toDocument(), "{'x': {'a': {'b': 9}}}");
  }

  @Test
  void shouldNotLayerAValueAssignedToTheWholeName() {
    // given
    final var builder =
        new InputMappingResultBuilder(
            name -> name.equals("x") ? asMsgPack("{'a': 1, 'b': 2}") : null);

    // when: x is assigned as a whole, not at a path inside it
    builder.put(List.of("x"), msgPack("{'a': 3}"));

    // then: shadowing is total
    assertEquality(((ContextValue.MsgPack) builder.getVariable("x")).buffer(), "{'a': 3}");
  }

  @Test
  void shouldNotLayerALevelRecreatedAfterTheWholeNameWasAssigned() {
    // given: x was assigned whole, so the value it shadowed is already gone
    final var builder =
        new InputMappingResultBuilder(
            name -> name.equals("x") ? asMsgPack("{'a': 1, 'b': 2}") : null);
    builder.put(List.of("x"), msgPack("1"));

    // when: a dotted target re-creates x as a fresh object
    builder.put(List.of("x", "a"), msgPack("2"));

    // then: the total shadow persists — b does not come back
    assertEquality(
        MappingResultBuilder.toMsgPack((ContextValue.Structure) builder.getVariable("x")),
        "{'a': 2}");
  }

  @Test
  void shouldNotLayerANestedLevelRecreatedAfterThatLevelWasAssigned() {
    // given: the case above one level down — this is why the flag is per level, not per name
    final var builder =
        new InputMappingResultBuilder(
            name -> name.equals("x") ? asMsgPack("{'a': {'b': 1}, 'd': 4}") : null);
    builder.put(List.of("x", "a"), msgPack("1"));

    // when
    builder.put(List.of("x", "a", "c"), msgPack("2"));

    // then: a stays totally shadowed, but x's untouched sibling d still falls through
    assertEquality(
        MappingResultBuilder.toMsgPack((ContextValue.Structure) builder.getVariable("x")),
        "{'a': {'c': 2}, 'd': 4}");
  }

  @Test
  void shouldNotLayerWhenTheShadowedValueIsNotAMap() {
    // given
    final var builder =
        new InputMappingResultBuilder(name -> name.equals("x") ? asMsgPack("5") : null);

    // when
    builder.put(List.of("x", "a"), msgPack("1"));

    // then: nothing to fall through to — and no POISON either, that is output-mapping-only
    assertEquality(
        MappingResultBuilder.toMsgPack((ContextValue.Structure) builder.getVariable("x")),
        "{'a': 1}");
    assertEquality(builder.toDocument(), "{'x': {'a': 1}}");
  }

  @Test
  void shouldNotLayerWhenNothingShadowsTheName() {
    // given
    final var builder = new InputMappingResultBuilder(name -> null);

    // when
    builder.put(List.of("x", "a"), msgPack("1"));

    // then
    assertEquality(
        MappingResultBuilder.toMsgPack((ContextValue.Structure) builder.getVariable("x")),
        "{'a': 1}");
  }

  @Test
  void shouldNotLetALayeredReadLeakIntoTheDocument() {
    // given
    final var builder =
        new InputMappingResultBuilder(
            name -> name.equals("x") ? asMsgPack("{'a': 1, 'b': 2}") : null);
    builder.put(List.of("x", "a"), msgPack("3"));

    // when: the layered read happens between two puts that build x
    assertEquality(
        MappingResultBuilder.toMsgPack((ContextValue.Structure) builder.getVariable("x")),
        "{'a': 3, 'b': 2}");
    builder.put(List.of("x", "c"), msgPack("9"));

    // then: the read did not add b to the accumulator
    assertEquality(builder.toDocument(), "{'x': {'a': 3, 'c': 9}}");
  }

  @Test
  void shouldNotLetALaterWriteLeakIntoAnEarlierRead() {
    // given
    builder.put(List.of("x", "a"), msgPack("1"));

    // when: a later mapping reads x, then another mapping writes into the same level
    final var read = builder.getVariable("x");
    builder.put(List.of("x", "b"), msgPack("2"));

    // then: the read is a snapshot of the level as it stood at that position
    assertThat(((ContextValue.Structure) read).entries()).containsOnlyKeys("a");
  }

  @Test
  void shouldNotLetALaterWriteLeakIntoAnEarlierNestedRead() {
    // given: the case above one level down, since the snapshot has to be deep to hold
    builder.put(List.of("x", "a", "b"), msgPack("1"));

    // when: a later mapping reads x, then another mapping writes into the nested level x.a
    final var read = builder.getVariable("x");
    builder.put(List.of("x", "a", "c"), msgPack("2"));

    // then: the read's nested a is a snapshot too — it must not gain c
    final var nestedA = (ContextValue.Structure) ((ContextValue.Structure) read).entries().get("a");
    assertThat(nestedA.entries()).containsOnlyKeys("b");
  }

  @Test
  void shouldNotThrowStackOverflowWhenLayeringADeeplyNestedLevel() {
    // given — a 50 000-segment target path whose shadowed value is a map at the top level, so the
    // layering traversal descends the whole structure
    final var builder =
        new InputMappingResultBuilder(name -> name.equals("a") ? asMsgPack("{'a': 1}") : null);
    final var targetPath =
        IntStream.range(0, 50_000).mapToObj(i -> "a").collect(Collectors.toList());
    builder.put(targetPath, msgPack("1"));

    // when / then — must not throw
    assertThatCode(() -> builder.getVariable("a")).doesNotThrowAnyException();
  }

  private static ContextValue msgPack(final String json) {
    return new ContextValue.MsgPack(asMsgPack(json));
  }
}
