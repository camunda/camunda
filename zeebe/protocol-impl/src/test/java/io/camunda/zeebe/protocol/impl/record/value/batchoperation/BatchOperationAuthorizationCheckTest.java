/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class BatchOperationAuthorizationCheckTest {

  /**
   * Bytes that brokers before this type wrote for the security library's RequiredAuthorization,
   * captured from MsgPackConverter's mixin for it.
   */
  static List<Arguments> checksWrittenBefore() {
    return List.of(
        Arguments.of(
            new BatchOperationAuthorizationCheck(
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.UPDATE_PROCESS_INSTANCE,
                List.of("a", "b"),
                Set.of("assignee"),
                true),
            "85ad7265736f757263655f74797065b250524f434553535f444546494e4954494f4eaf7065726d697373"
                + "696f6e5f74797065b75550444154455f50524f434553535f494e5354414e4345ac7265736f7572"
                + "63655f69647392a161a162b77265736f757263655f70726f70657274795f6e616d657391a86173"
                + "7369676e6565aa7472616e736974697665c3"),
        Arguments.of(
            new BatchOperationAuthorizationCheck(
                AuthorizationResourceType.BATCH, PermissionType.CREATE, null, null, false),
            "85ad7265736f757263655f74797065a54241544348af7065726d697373696f6e5f74797065a643524541"
                + "5445ac7265736f757263655f696473c0b77265736f757263655f70726f70657274795f6e616d6573"
                + "c0aa7472616e736974697665c2"));
  }

  @ParameterizedTest
  @MethodSource("checksWrittenBefore")
  void shouldWriteTheBytesEarlierVersionsWrote(
      final BatchOperationAuthorizationCheck check, final String bytesWrittenBefore) {
    // when
    final var bytes = MsgPackConverter.convertToMsgPack(check);

    // then
    assertThat(HexFormat.of().formatHex(bytes)).isEqualTo(bytesWrittenBefore);
  }

  @ParameterizedTest
  @MethodSource("checksWrittenBefore")
  void shouldReadTheBytesEarlierVersionsWrote(
      final BatchOperationAuthorizationCheck check, final String bytesWrittenBefore) {
    // when
    final var read =
        MsgPackConverter.convertToObject(
            new UnsafeBuffer(HexFormat.of().parseHex(bytesWrittenBefore)),
            BatchOperationAuthorizationCheck.class);

    // then
    assertThat(read).isEqualTo(check);
  }
}
