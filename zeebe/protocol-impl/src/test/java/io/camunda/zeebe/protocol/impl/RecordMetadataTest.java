/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl;

import static io.camunda.zeebe.auth.Authorization.AUTHORIZED_USERNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.RecordMetadataDecoder;
import io.camunda.zeebe.protocol.record.RecordMetadataEncoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class RecordMetadataTest {

  @Test
  void shouldEncodeDecodeMetadataWithNullValues() {
    // given
    final RecordMetadata metadata = new RecordMetadata();

    // when
    encodeDecode(metadata);

    // then
    assertThat(metadata.getRecordType()).isEqualTo(RecordType.NULL_VAL);
    assertThat(metadata.getRequestId()).isEqualTo(RecordMetadataEncoder.requestIdNullValue());
    assertThat(metadata.getRequestStreamId())
        .isEqualTo(RecordMetadataEncoder.requestStreamIdNullValue());
    assertThat(metadata.getValueType()).isEqualTo(ValueType.NULL_VAL);
    assertThat(metadata.getIntent()).isEqualTo(Intent.UNKNOWN);
    assertThat(metadata.getRejectionType()).isEqualTo(RejectionType.NULL_VAL);
    assertThat(metadata.getRejectionReason()).isEmpty();
    assertThat(metadata.getAuthorization()).isEqualTo(AuthInfo.EMPTY);
    assertThat(metadata.getAgent()).isNull();
    assertThat(metadata.getBrokerVersion()).isEqualTo(RecordMetadata.CURRENT_BROKER_VERSION);
    assertThat(metadata.getRecordVersion()).isEqualTo(RecordMetadata.DEFAULT_RECORD_VERSION);
  }

  @Test
  void shouldEncodeDecodeAuthorization() {
    // given
    final var authInfo =
        AuthInfo.ofClaims(Map.of(AUTHORIZED_USERNAME, "user"))
            .withFormat(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
    final var metadata = new RecordMetadata().authorization(authInfo);

    // when
    encodeDecode(metadata);

    // then
    assertThat(metadata.getAuthorization().getFormat())
        .isEqualTo(AuthInfo.AuthDataFormat.PRE_AUTHORIZED);
    assertThat(metadata.getAuthorization().toClaimsMap())
        .isEqualTo(Map.of(AUTHORIZED_USERNAME, "user"));
  }

  @Test
  void shouldKeepAuthorizationWhenRewritingDecodedMetadata() {
    // given
    final var authInfo = AuthInfo.ofClaims(Map.of(AUTHORIZED_USERNAME, "user"));
    final var original = new RecordMetadata().authorization(authInfo);
    final var decoded = new RecordMetadata();
    decoded.wrap(write(original), 0, original.getLength());

    // when
    final var rewritten = write(decoded);
    final var reread = new RecordMetadata();
    reread.wrap(rewritten, 0, rewritten.capacity());

    // then
    assertThat(reread.getAuthorization().toClaimsMap())
        .isEqualTo(Map.of(AUTHORIZED_USERNAME, "user"));
  }

  @Test
  void shouldWriteEmptyAuthorizationAsEncodedAuthInfoByDefault() {
    // given
    final var metadata = new RecordMetadata();

    // when
    final var buffer = write(metadata);

    // then - as in previous versions, not as a zero-length field
    final var decoder = new RecordMetadataDecoder();
    final var header = new MessageHeaderDecoder().wrap(buffer, 0);
    decoder.wrap(buffer, header.encodedLength(), header.blockLength(), header.version());
    decoder.skipRejectionReason();
    assertThat(decoder.authorizationLength()).isEqualTo(AuthInfo.EMPTY.getLength()).isPositive();
  }

  @Test
  void shouldReportSameLengthBeforeAndAfterDecodingAuthorization() {
    // given
    final var original =
        new RecordMetadata().authorization(AuthInfo.ofClaims(Map.of(AUTHORIZED_USERNAME, "user")));
    final var decoded = new RecordMetadata();
    decoded.wrap(write(original), 0, original.getLength());
    final var lengthBefore = decoded.getLength();

    // when
    decoded.getAuthorization();

    // then
    assertThat(lengthBefore).isEqualTo(original.getLength());
    assertThat(decoded.getLength()).isEqualTo(lengthBefore);
  }

  @Test
  void shouldBeEqualWhetherOrNotAuthorizationWasDecoded() {
    // given
    final var original =
        new RecordMetadata().authorization(AuthInfo.ofClaims(Map.of(AUTHORIZED_USERNAME, "user")));
    final var buffer = write(original);
    final var lazy = new RecordMetadata();
    lazy.wrap(buffer, 0, buffer.capacity());
    final var eager = new RecordMetadata();
    eager.wrap(buffer, 0, buffer.capacity());
    eager.getAuthorization();

    // then
    assertThat(lazy).isEqualTo(eager).hasSameHashCodeAs(eager).hasToString(eager.toString());
    assertThat(lazy).isEqualTo(original);
  }

  @Test
  void shouldNotMentionAuthorizationInToStringWhenEmpty() {
    // given
    final var buffer = write(new RecordMetadata());
    final var metadata = new RecordMetadata();
    metadata.wrap(buffer, 0, buffer.capacity());

    // then
    assertThat(metadata.toString()).doesNotContain("authorization");
  }

  @Test
  void shouldResetAuthorizationOfWrappedMetadata() {
    // given
    final var withAuthorization =
        new RecordMetadata().authorization(AuthInfo.ofClaims(Map.of(AUTHORIZED_USERNAME, "user")));
    final var metadata = new RecordMetadata();
    metadata.wrap(write(withAuthorization), 0, withAuthorization.getLength());

    // when
    metadata.reset();

    // then
    assertThat(metadata.getAuthorization()).isEqualTo(AuthInfo.EMPTY);
    assertThat(metadata.getLength()).isEqualTo(new RecordMetadata().getLength());
  }

  @Test
  void shouldRejectNullAuthorization() {
    // given
    final var metadata = new RecordMetadata();

    // when / then
    assertThatThrownBy(() -> metadata.authorization(null)).isInstanceOf(NullPointerException.class);
  }

  private static UnsafeBuffer write(final RecordMetadata metadata) {
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[metadata.getLength()]);
    metadata.write(buffer, 0);
    return buffer;
  }

  private void encodeDecode(final RecordMetadata metadata) {
    // encode
    final UnsafeBuffer buffer = write(metadata);

    // decode
    metadata.reset();
    metadata.wrap(buffer, 0, buffer.capacity());
  }
}
