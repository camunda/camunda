/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.core.JsonPointer;
import io.camunda.zeebe.engine.processing.clustervariable.ClusterVariableSecretReferenceScanner.DetectedReference;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pure unit tests for {@link ClusterVariableSecretReferenceScanner}, covering the same transferable
 * scenarios as {@link
 * io.camunda.zeebe.engine.processing.deployment.model.element.SecretReferenceTest} (the FEEL-only
 * ones, such as string-literal-vs-expression and iterator shadowing, do not apply here since the
 * scanner walks a plain msgpack value, not a parsed FEEL AST).
 */
final class ClusterVariableSecretReferenceScannerTest {

  private final ClusterVariableSecretReferenceScanner scanner =
      new ClusterVariableSecretReferenceScanner();

  @Test
  void shouldDetectReferenceAtRootScalar() {
    // given a value that is the string itself, not nested in an object
    final var references = scan("\"camunda.secrets.token\"");

    // then the pointer of a root-level leaf is the empty string
    assertThat(references)
        .extracting(DetectedReference::name, DetectedReference::pointer)
        .containsExactly(tuple("token", ""));
  }

  @Test
  void shouldDetectReferenceInObjectField() {
    // given
    final var references = scan("{\"auth\": \"camunda.secrets.token\"}");

    // then
    assertThat(references)
        .extracting(DetectedReference::name, DetectedReference::pointer)
        .containsExactly(tuple("token", "/auth"));
  }

  @Test
  void shouldDetectReferenceInArrayElement() {
    // given
    final var references = scan("{\"list\": [\"camunda.secrets.token\"]}");

    // then
    assertThat(references)
        .extracting(DetectedReference::name, DetectedReference::pointer)
        .containsExactly(tuple("token", "/list/0"));
  }

  @Test
  void shouldDetectReferenceInNestedObject() {
    // given
    final var references = scan("{\"a\": {\"b\": \"camunda.secrets.token\"}}");

    // then
    assertThat(references)
        .extracting(DetectedReference::name, DetectedReference::pointer)
        .containsExactly(tuple("token", "/a/b"));
  }

  @Test
  void shouldReturnEmptyWhenValueHasNoSecretReference() {
    // given a SECRET_REFERENCE-shaped value that does not contain any camunda.secrets.* text
    final var references = scan("{\"auth\": \"plain value\"}");

    // then
    assertThat(references).isEmpty();
  }

  @Test
  void shouldDetectTwoDistinctReferencesInOneLeaf() {
    // given
    final var references = scan("\"camunda.secrets.a and camunda.secrets.b\"");

    // then
    assertThat(references)
        .extracting(DetectedReference::name, DetectedReference::pointer)
        .containsExactlyInAnyOrder(tuple("a", ""), tuple("b", ""));
  }

  @Test
  void shouldDedupeRepeatedReferenceWithinOneLeaf() {
    // given the same reference occurring twice in a single leaf
    final var references = scan("\"camunda.secrets.a and camunda.secrets.a\"");

    // then it collapses into a single entry
    assertThat(references)
        .extracting(DetectedReference::name, DetectedReference::pointer)
        .containsExactly(tuple("a", ""));
  }

  @Test
  void shouldReportSameReferenceNameAtTwoDifferentLeavesSeparately() {
    // given the same reference name used at two different leaves
    final var references =
        scan("{\"a\": \"camunda.secrets.token\", \"b\": \"camunda.secrets.token\"}");

    // then both leaves are reported, each with its own pointer
    assertThat(references)
        .extracting(DetectedReference::name, DetectedReference::pointer)
        .containsExactlyInAnyOrder(tuple("token", "/a"), tuple("token", "/b"));
  }

  @Test
  void shouldNotFusePrefixCollidingReferences() {
    // given a leaf where one reference name is a prefix of another
    final var references = scan("\"camunda.secrets.token and camunda.secrets.token2\"");

    // then both distinct names are reported, not just the longer one
    assertThat(references)
        .extracting(DetectedReference::name)
        .containsExactlyInAnyOrder("token", "token2");
  }

  @Test
  void shouldCaptureHyphenatedNameInFull() {
    // given a name containing a hyphen, as the backing stores routinely hold
    final var references = scan("\"camunda.secrets.my-name\"");

    // then the whole name is captured. Truncating at the hyphen used to resolve the unrelated
    // secret 'my' whenever one existed, which is the failure this charset closes
    assertThat(references).extracting(DetectedReference::name).containsExactly("my-name");
  }

  @ParameterizedTest(name = "camunda.secrets.{0} captures {1}")
  @CsvSource({"my.name, my", "my name, my", "my*name, my"})
  void shouldStopNameAtFirstCharacterOutsideTheNameCharset(
      final String written, final String captured) {
    // given a name carrying a character the reference charset excludes: a dot would make the
    // reference a fourth FEEL segment, and whitespace and glob characters are no FEEL identifier
    final var references = scan("\"camunda.secrets." + written + "\"");

    // then the name ends where the charset does, documenting the boundary of the shared reference
    // pattern (SecretReference.REFERENCE_PATTERN)
    assertThat(references).extracting(DetectedReference::name).containsExactly(captured);
  }

  @ParameterizedTest(name = "camunda.secrets.{0} captures {1}")
  @CsvSource({"token-, token-", "token-v2, token-v2", "token-camunda.secrets.other, token-camunda"})
  void shouldReadAHyphenAsPartOfTheNameRatherThanEndingIt(
      final String written, final String captured) {
    // given a reference followed by a hyphen: trailing, suffixed, and with a second reference
    // joined on. The pattern is not anchored, so the hyphen never ends a name
    final var references = scan("\"camunda.secrets." + written + "\"");

    // then the whole run is one name. A composed value that meant 'camunda.secrets.token' plus
    // literal text after the hyphen therefore stops resolving: the store answers NOT_FOUND and the
    // incident names the captured string. That is the deliberate trade — the narrower charset
    // instead resolved the unrelated secret 'token' silently, which is what #60364 is about
    assertThat(references).extracting(DetectedReference::name).containsExactly(captured);
  }

  @Test
  void shouldProduceAPointerThatRoundTripsForFieldNamesNeedingRfc6901Escaping() {
    // given a field name containing both '/' and '~', the two characters RFC 6901 requires
    // escaping for
    final var references = scan("{\"a/b~c\": \"camunda.secrets.token\"}");

    // then
    assertThat(references)
        .extracting(DetectedReference::name, DetectedReference::pointer)
        .containsExactly(tuple("token", "/a~1b~0c"));

    // and the pointer round-trips back to the original field name
    final var pointer = JsonPointer.compile(references.get(0).pointer());
    assertThat(pointer.getMatchingProperty()).isEqualTo("a/b~c");
  }

  private List<DetectedReference> scan(final String json) {
    final var result = scanner.scan(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(json)));
    assertThat(result.isRight()).describedAs("scan result: %s", result).isTrue();
    return result.get();
  }
}
