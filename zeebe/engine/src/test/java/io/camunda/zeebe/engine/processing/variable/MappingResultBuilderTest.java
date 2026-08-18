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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class MappingResultBuilderTest {

  private final MappingResultBuilder builder = MappingResultBuilder.forInputMappings(name -> null);

  @Test
  void shouldBuildEmptyDocument() {
    assertEquality(builder.toDocument(), "{}");
  }

  @Test
  void shouldPutTopLevelValue() {
    // when
    builder.put(List.of("x"), asMsgPack("1"));

    // then
    assertEquality(builder.toDocument(), "{'x': 1}");
  }

  @Test
  void shouldPutNestedValue() {
    // when
    builder.put(List.of("a", "b", "c"), asMsgPack("1"));

    // then
    assertEquality(builder.toDocument(), "{'a': {'b': {'c': 1}}}");
  }

  @Test
  void shouldMergeSiblingNestedTargets() {
    // when
    builder.put(List.of("a", "b"), asMsgPack("1"));
    builder.put(List.of("a", "c"), asMsgPack("2"));

    // then
    assertEquality(builder.toDocument(), "{'a': {'b': 1, 'c': 2}}");
  }

  @Test
  void shouldOverrideDuplicateTarget() {
    // when
    builder.put(List.of("x"), asMsgPack("1"));
    builder.put(List.of("x"), asMsgPack("2"));

    // then
    assertEquality(builder.toDocument(), "{'x': 2}");
  }

  @Test
  void shouldReplaceValueWithNestedObject() {
    // given: 'a' first mapped as a plain value, then a nested target descends into it
    builder.put(List.of("a"), asMsgPack("1"));

    // when
    builder.put(List.of("a", "b"), asMsgPack("2"));

    // then: structural last-wins
    assertEquality(builder.toDocument(), "{'a': {'b': 2}}");
  }

  @Test
  void shouldReplaceNestedObjectWithValue() {
    // given
    builder.put(List.of("a", "b"), asMsgPack("1"));

    // when
    builder.put(List.of("a"), asMsgPack("2"));

    // then
    assertEquality(builder.toDocument(), "{'a': 2}");
  }

  @Test
  void shouldGetTopLevelVariable() {
    // given
    builder.put(List.of("x"), asMsgPack("1"));

    // when + then
    assertEquality(builder.getVariable("x"), "1");
  }

  @Test
  void shouldGetNestedStructureAsVariable() {
    // given
    builder.put(List.of("a", "b"), asMsgPack("1"));

    // when + then
    assertEquality(builder.getVariable("a"), "{'b': 1}");
  }

  @Test
  void shouldReturnNullForUnknownVariable() {
    assertThat(builder.getVariable("unknown")).isNull();
  }

  @Test
  void shouldCopyTheValueBuffer() {
    // given: evaluation result buffers are transient and may be reused by the next evaluation
    final var value = asMsgPack("1");
    builder.put(List.of("x"), value);

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
    builder.put(targetPath, asMsgPack("1"));

    // when / then — must not throw
    assertThatCode(builder::toDocument).doesNotThrowAnyException();
  }

  @Test
  void shouldSeedNestedTargetFromScopeValue() {
    // given: scope has a = {'c': 2}
    final var builder =
        MappingResultBuilder.forOutputMappings(
            path -> path.equals(List.of("a")) ? asMsgPack("{'c': 2}") : null);

    // when
    builder.put(List.of("a", "b"), asMsgPack("1"));

    // then: existing sibling 'c' survives the merge
    assertEquality(builder.toDocument(), "{'a': {'b': 1, 'c': 2}}");
  }

  @Test
  void shouldSeedEveryPathLevelFromScopeValue() {
    // given: scope has a = {'b': {'d': 2}, 'e': 3}
    final var scope =
        Map.of(
            List.of("a"), asMsgPack("{'b': {'d': 2}, 'e': 3}"),
            List.of("a", "b"), asMsgPack("{'d': 2}"));
    final var builder = MappingResultBuilder.forOutputMappings(scope::get);

    // when
    builder.put(List.of("a", "b", "c"), asMsgPack("1"));

    // then: siblings survive at both levels
    assertEquality(builder.toDocument(), "{'a': {'b': {'c': 1, 'd': 2}, 'e': 3}}");
  }

  @Test
  void shouldPoisonLevelWhenScopeValueIsNotAMap() {
    // given: scope has a = 5
    final var builder =
        MappingResultBuilder.forOutputMappings(
            path -> path.equals(List.of("a")) ? asMsgPack("5") : null);

    // when
    builder.put(List.of("a", "b"), asMsgPack("1"));

    // then: the level is null, like FEEL's context merge(5, {...})
    assertEquality(builder.toDocument(), "{'a': null}");
    assertEquality(builder.getVariable("a"), "null");
  }

  @Test
  void shouldDiscardFurtherPutsUnderPoisonedLevel() {
    final var builder =
        MappingResultBuilder.forOutputMappings(
            path -> path.equals(List.of("a")) ? asMsgPack("5") : null);

    // when
    builder.put(List.of("a", "b"), asMsgPack("1"));
    builder.put(List.of("a", "c"), asMsgPack("2"));

    // then
    assertEquality(builder.toDocument(), "{'a': null}");
  }

  @Test
  void shouldReplacePoisonedLevelWithPlainTargetPut() {
    final var builder =
        MappingResultBuilder.forOutputMappings(
            path -> path.equals(List.of("a")) ? asMsgPack("5") : null);

    // when: a nested put poisons 'a', then a plain put replaces it
    builder.put(List.of("a", "b"), asMsgPack("1"));
    builder.put(List.of("a"), asMsgPack("7"));

    // then
    assertEquality(builder.toDocument(), "{'a': 7}");
  }

  @Test
  void shouldReseedFromScopeWhenNestedPutFollowsPlainPut() {
    // given: scope a = {'c': 2}; a plain put stored a value buffer for 'a'
    final var builder =
        MappingResultBuilder.forOutputMappings(
            path -> path.equals(List.of("a")) ? asMsgPack("{'c': 2}") : null);
    builder.put(List.of("a"), asMsgPack("{'z': 9}"));

    // when: the shape flips back to a map — the plain mapping's value is discarded and the fresh
    // level is seeded from the SCOPE value, not the previously mapped value
    builder.put(List.of("a", "b"), asMsgPack("1"));

    // then
    assertEquality(builder.toDocument(), "{'a': {'b': 1, 'c': 2}}");
  }

  @Test
  void shouldSeedFreshMapWhenScopeValueIsNil() {
    // given: scope has a = null — FEEL's `if (a != null)` takes the else branch
    final var builder =
        MappingResultBuilder.forOutputMappings(
            path -> path.equals(List.of("a")) ? asMsgPack("null") : null);

    // when
    builder.put(List.of("a", "b"), asMsgPack("1"));

    // then
    assertEquality(builder.toDocument(), "{'a': {'b': 1}}");
  }

  @Test
  void shouldLayerNestedLevelOverShadowedValue() {
    // given: the scope chain resolves x to {'a': 1, 'b': 2}
    final var builder =
        MappingResultBuilder.forInputMappings(
            name -> name.equals("x") ? asMsgPack("{'a': 1, 'b': 2}") : null);

    // when: only x.a is mapped
    builder.put(List.of("x", "a"), asMsgPack("3"));

    // then: the read layers the mapped key over the shadowed value ...
    assertEquality(builder.getVariable("x"), "{'a': 3, 'b': 2}");
    // ... but the document keeps only what was mapped
    assertEquality(builder.toDocument(), "{'x': {'a': 3}}");
  }

  @Test
  void shouldLayerEveryLevelOfANestedLevelOverShadowedValue() {
    // given: a shadowed value nested as deeply as the target path
    final var builder =
        MappingResultBuilder.forInputMappings(
            name -> name.equals("x") ? asMsgPack("{'a': {'b': 1, 'c': 2}, 'd': 4}") : null);

    // when
    builder.put(List.of("x", "a", "b"), asMsgPack("9"));

    // then: unmapped keys survive at every level, which a top-level-only merge would not do
    assertEquality(builder.getVariable("x"), "{'a': {'b': 9, 'c': 2}, 'd': 4}");
    assertEquality(builder.toDocument(), "{'x': {'a': {'b': 9}}}");
  }

  @Test
  void shouldNotLayerAValueAssignedToTheWholeName() {
    // given
    final var builder =
        MappingResultBuilder.forInputMappings(
            name -> name.equals("x") ? asMsgPack("{'a': 1, 'b': 2}") : null);

    // when: x is assigned as a whole, not at a path inside it
    builder.put(List.of("x"), asMsgPack("{'a': 3}"));

    // then: shadowing is total
    assertEquality(builder.getVariable("x"), "{'a': 3}");
  }

  @Test
  void shouldNotLayerALevelRecreatedAfterTheWholeNameWasAssigned() {
    // given: x was assigned whole, so the value it shadowed is already gone
    final var builder =
        MappingResultBuilder.forInputMappings(
            name -> name.equals("x") ? asMsgPack("{'a': 1, 'b': 2}") : null);
    builder.put(List.of("x"), asMsgPack("1"));

    // when: a dotted target re-creates x as a fresh object
    builder.put(List.of("x", "a"), asMsgPack("2"));

    // then: the total shadow persists — b does not come back
    assertEquality(builder.getVariable("x"), "{'a': 2}");
  }

  @Test
  void shouldNotLayerANestedLevelRecreatedAfterThatLevelWasAssigned() {
    // given: the case above one level down — this is why the flag is per level, not per name
    final var builder =
        MappingResultBuilder.forInputMappings(
            name -> name.equals("x") ? asMsgPack("{'a': {'b': 1}, 'd': 4}") : null);
    builder.put(List.of("x", "a"), asMsgPack("1"));

    // when
    builder.put(List.of("x", "a", "c"), asMsgPack("2"));

    // then: a stays totally shadowed, but x's untouched sibling d still falls through
    assertEquality(builder.getVariable("x"), "{'a': {'c': 2}, 'd': 4}");
  }

  @Test
  void shouldNotLayerWhenTheShadowedValueIsNotAMap() {
    // given
    final var builder =
        MappingResultBuilder.forInputMappings(name -> name.equals("x") ? asMsgPack("5") : null);

    // when
    builder.put(List.of("x", "a"), asMsgPack("1"));

    // then: nothing to fall through to — and no POISON either, that is output-mapping-only
    assertEquality(builder.getVariable("x"), "{'a': 1}");
    assertEquality(builder.toDocument(), "{'x': {'a': 1}}");
  }

  @Test
  void shouldNotLayerWhenNothingShadowsTheName() {
    // given
    final var builder = MappingResultBuilder.forInputMappings(name -> null);

    // when
    builder.put(List.of("x", "a"), asMsgPack("1"));

    // then
    assertEquality(builder.getVariable("x"), "{'a': 1}");
  }

  @Test
  void shouldNotLetALayeredReadLeakIntoTheDocument() {
    // given
    final var builder =
        MappingResultBuilder.forInputMappings(
            name -> name.equals("x") ? asMsgPack("{'a': 1, 'b': 2}") : null);
    builder.put(List.of("x", "a"), asMsgPack("3"));

    // when: the layered read happens between two puts that build x
    assertEquality(builder.getVariable("x"), "{'a': 3, 'b': 2}");
    builder.put(List.of("x", "c"), asMsgPack("9"));

    // then: the read did not add b to the accumulator
    assertEquality(builder.toDocument(), "{'x': {'a': 3, 'c': 9}}");
  }

  @Test
  void shouldNotThrowStackOverflowWhenLayeringADeeplyNestedLevel() {
    // given — a 50 000-segment target path whose shadowed value is a map at the top level, so the
    // layering traversal descends the whole structure
    final var builder =
        MappingResultBuilder.forInputMappings(
            name -> name.equals("a") ? asMsgPack("{'a': 1}") : null);
    final var targetPath =
        IntStream.range(0, 50_000).mapToObj(i -> "a").collect(Collectors.toList());
    builder.put(targetPath, asMsgPack("1"));

    // when / then — must not throw
    assertThatCode(() -> builder.getVariable("a")).doesNotThrowAnyException();
  }
}
