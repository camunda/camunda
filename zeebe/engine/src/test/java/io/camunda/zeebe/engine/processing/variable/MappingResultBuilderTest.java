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
import org.agrona.DirectBuffer;
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
  void shouldCompletePartialTargetFromScopeValueOnLookup() {
    // given: scope has foo = {'bar': 9, 'baz': 2}, and a nested target has produced foo.bar
    final var builder = inputBuilder(Map.of("foo", asMsgPack("{'bar': 9, 'baz': 2}")));
    builder.put(List.of("foo", "bar"), asMsgPack("1"));

    // when + then: 'baz' was not mapped, so it still resolves against the scope value
    assertEquality(builder.getVariable("foo"), "{'bar': 1, 'baz': 2}");
  }

  @Test
  void shouldNotApplyTheScopeValueToTheDocument() {
    // given: completing a lookup must not turn a nested input target into a merge - input mappings
    // overwrite the scope variable outright
    final var builder = inputBuilder(Map.of("foo", asMsgPack("{'bar': 9, 'baz': 2}")));
    builder.put(List.of("foo", "bar"), asMsgPack("1"));

    // when: the lookup that completes from the scope happens before the document is built
    builder.getVariable("foo");

    // then: 'baz' is absent - only the mapped property is applied
    assertEquality(builder.toDocument(), "{'foo': {'bar': 1}}");
  }

  @Test
  void shouldCompletePartialTargetAtEveryNestingLevelOnLookup() {
    // given: without recursion the dead end would just move one level down
    final var builder =
        inputBuilder(Map.of("foo", asMsgPack("{'bar': {'x': 9, 'y': 2}, 'other': 3}")));
    builder.put(List.of("foo", "bar", "x"), asMsgPack("1"));

    // when + then
    assertEquality(builder.getVariable("foo"), "{'bar': {'x': 1, 'y': 2}, 'other': 3}");
  }

  @Test
  void shouldPreferMappedPropertyOverScopeValueOnLookup() {
    // given
    final var builder = inputBuilder(Map.of("foo", asMsgPack("{'bar': 9}")));
    builder.put(List.of("foo", "bar"), asMsgPack("1"));

    // when + then: the just-mapped value wins, the scope only fills the gaps
    assertEquality(builder.getVariable("foo"), "{'bar': 1}");
  }

  @Test
  void shouldNotCompleteCompletedTargetFromScopeValue() {
    // given: a mapping produced 'foo' as a whole, so it is complete rather than under construction
    final var builder = inputBuilder(Map.of("foo", asMsgPack("{'bar': 9, 'baz': 2}")));
    builder.put(List.of("foo"), asMsgPack("{'bar': 1}"));

    // when + then: it shadows the scope value outright
    assertEquality(builder.getVariable("foo"), "{'bar': 1}");
  }

  @Test
  void shouldNotCompletePartialTargetFromNonObjectScopeValue() {
    // given: scope has foo = 5 - there is no object to resolve the remaining properties against
    final var builder = inputBuilder(Map.of("foo", asMsgPack("5")));
    builder.put(List.of("foo", "bar"), asMsgPack("1"));

    // when + then: no poisoning either - that is output-mapping behaviour
    assertEquality(builder.getVariable("foo"), "{'bar': 1}");
    assertEquality(builder.toDocument(), "{'foo': {'bar': 1}}");
  }

  @Test
  void shouldNotCompletePartialTargetFromNilScopeValue() {
    // given
    final var builder = inputBuilder(Map.of("foo", asMsgPack("null")));
    builder.put(List.of("foo", "bar"), asMsgPack("1"));

    // when + then
    assertEquality(builder.getVariable("foo"), "{'bar': 1}");
  }

  @Test
  void shouldNotCompletePartialTargetOfOutputBuilderOnLookup() {
    // given: an output builder already seeds every level while accumulating, so a lookup needs no
    // further fallback
    final var builder =
        MappingResultBuilder.forOutputMappings(
            path -> path.equals(List.of("a")) ? asMsgPack("{'c': 2}") : null);

    // when
    builder.put(List.of("a", "b"), asMsgPack("1"));

    // then
    assertEquality(builder.getVariable("a"), "{'c': 2, 'b': 1}");
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

  private static MappingResultBuilder inputBuilder(final Map<String, DirectBuffer> scope) {
    return MappingResultBuilder.forInputMappings(scope::get);
  }
}
