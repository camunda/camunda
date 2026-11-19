/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.encoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.record.InternalOperationType;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import org.junit.jupiter.api.Test;

/**
 * A new reference must name a known type, while a reference read from the log keeps a code this
 * version doesn't know, so it can be passed on unchanged.
 */
final class InternalOperationReferenceTest {

  @Test
  void shouldRejectUnknownTypeForNewReference() {
    // when - then
    assertThatThrownBy(() -> InternalOperationReference.of(InternalOperationType.UNKNOWN, 123L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected a known internal operation type");
  }

  @Test
  void shouldKeepUnknownStoredTypeCode() {
    // when
    final var reference = InternalOperationReference.ofStored(200, 123L);

    // then
    assertThat(reference.type()).isEqualTo(InternalOperationType.UNKNOWN);
    assertThat(reference.typeCode()).isEqualTo(200);
    assertThat(reference.isPresent()).isTrue();
    assertThat(reference).hasToString("UNKNOWN(200):123");
  }

  @Test
  void shouldRejectStoredTypeCodeOutOfRange() {
    // when - then
    assertThatThrownBy(() -> InternalOperationReference.ofStored(255, 123L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 0 and 254, but was 255");
    assertThatThrownBy(() -> InternalOperationReference.ofStored(-1, 123L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldTreatStoredNullValuesAsNone() {
    // when
    final var reference =
        InternalOperationReference.ofStored(
            InternalOperationType.UNSPECIFIED.code(),
            RecordMetadataDecoder.internalOperationReferenceNullValue());

    // then
    assertThat(reference).isSameAs(InternalOperationReference.NONE);
    assertThat(reference.isPresent()).isFalse();
  }
}
