/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class VariableMetadataRecordTest {

  @Test
  void shouldParseProtectionModeFromString() {
    // when
    final var metadata = wrap("{\"protectionModes\":[\"REDACT\"]}");

    // then
    assertThat(metadata.getProtectionModes()).containsExactly(ProtectionMode.REDACT);
  }

  @Test
  void shouldParseNoProtectionModes() {
    // when
    final var metadata = wrap("{\"protectionModes\":[]}");

    // then
    assertThat(metadata.getProtectionModes()).isEmpty();
  }

  @Test
  void shouldCollapseDuplicateProtectionModes() {
    // when
    final var metadata = wrap("{\"protectionModes\":[\"REDACT\",\"REDACT\"]}");

    // then
    assertThat(metadata.getProtectionModes()).containsExactly(ProtectionMode.REDACT);
  }

  @Test
  void shouldRejectUnknownProtectionMode() {
    // given a mode this version does not know, e.g. written by a newer version
    final var json = "{\"protectionModes\":[\"SCRAMBLE\"]}";

    // when / then
    assertThatThrownBy(() -> wrap(json)).hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectProtectionModeWithDifferentCase() {
    // given
    final var json = "{\"protectionModes\":[\"redact\"]}";

    // when / then
    assertThatThrownBy(() -> wrap(json)).hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  private static VariableMetadataRecord wrap(final String json) {
    final var msgPack = MsgPackConverter.convertToMsgPack(json);
    final var metadata = new VariableMetadataRecord();
    metadata.wrap(new UnsafeBuffer(msgPack), 0, msgPack.length);
    return metadata;
  }
}
