/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secret;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class SecretMaskerTest {

  @Test
  void shouldReplaceTopLevelSecretValuesWithMaskedSentinel() {
    // given — a msgpack document mixing a regular variable and a resolved secret entry, the
    // shape JobVariablesCollector produces when a worker fetches a secret.
    final var doc = encode(Map.of("name", "alice", "camunda.secret.SLACK_BOT_TOKEN", "xoxb-real"));

    // when
    final var masked = SecretMasker.maskSecretsInDocument(doc);

    // then — the secret-keyed value is replaced; the unrelated variable is untouched.
    final var decoded = MsgPackConverter.convertToMap(masked);
    assertThat(decoded).containsEntry("name", "alice");
    assertThat(decoded).containsEntry("camunda.secret.SLACK_BOT_TOKEN", "***");
  }

  @Test
  void shouldMaskMultipleSecretsInOneDocument() {
    final var doc =
        encode(
            new LinkedHashMap<String, Object>() {
              {
                put("camunda.secret.A", "a-value");
                put("camunda.secret.B", "b-value");
                put("other", "unchanged");
              }
            });

    final var masked = SecretMasker.maskSecretsInDocument(doc);

    final var decoded = MsgPackConverter.convertToMap(masked);
    assertThat(decoded)
        .containsEntry("camunda.secret.A", "***")
        .containsEntry("camunda.secret.B", "***")
        .containsEntry("other", "unchanged");
  }

  @Test
  void shouldReturnSameBufferWhenNoSecretKeysPresent() {
    // given — the common case: a regular variable document with no resolved secret entries.
    final var doc = encode(Map.of("name", "alice", "amount", 42));

    // when
    final var masked = SecretMasker.maskSecretsInDocument(doc);

    // then — masker short-circuits and returns the original buffer reference, avoiding the
    // msgpack round-trip on every activation.
    assertThat(masked).isSameAs(doc);
  }

  @Test
  void shouldOnlyMatchExactNamespacePrefix() {
    // given — keys whose names happen to contain "camunda.secret." but don't START with it,
    // and a key that has the prefix but no suffix. The matcher must use startsWith, not
    // contains, to avoid false positives.
    final var doc =
        encode(Map.of("about-camunda.secret.token", "value", "camunda.secret.X", "real"));

    final var masked = SecretMasker.maskSecretsInDocument(doc);

    final var decoded = MsgPackConverter.convertToMap(masked);
    assertThat(decoded)
        .containsEntry("about-camunda.secret.token", "value")
        .containsEntry("camunda.secret.X", "***");
  }

  @Test
  void shouldReturnInputWhenDocumentIsNullOrEmpty() {
    assertThat(SecretMasker.maskSecretsInDocument(null)).isNull();
    assertThat(SecretMasker.maskSecretsInDocument(new UnsafeBuffer()))
        .matches(b -> b.capacity() == 0);
  }

  @Test
  void shouldRecogniseSecretReferenceInExpressionText() {
    assertThat(SecretMasker.expressionReferencesSecret("=camunda.secret.FOO")).isTrue();
    assertThat(SecretMasker.expressionReferencesSecret("=\"Bearer \" + camunda.secret.FOO"))
        .isTrue();
    assertThat(SecretMasker.expressionReferencesSecret("=42")).isFalse();
    assertThat(SecretMasker.expressionReferencesSecret("camunda.vars.cluster.KEY")).isFalse();
    assertThat(SecretMasker.expressionReferencesSecret(null)).isFalse();
  }

  @Test
  void maskedStringBufferDecodesToMaskedValue() {
    final var buf = SecretMasker.maskedAsMsgPackString();
    assertThat(MsgPackConverter.convertToJson(buf)).isEqualTo("\"***\"");
  }

  private static org.agrona.DirectBuffer encode(final Map<String, ?> map) {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(map));
  }
}
