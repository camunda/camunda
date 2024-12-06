/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class AuthInfoTest {

  @Test
  void shouldEncodeDecodeAuthInfo() {
    // given
    final String testData = "test-data";
    final AuthInfo authInfo = new AuthInfo();
    final Map<String, Object> authInfoMap = Map.of("key", "value");
    authInfo.setAuthData(authInfoMap);

    // when
    encodeDecode(authInfo);

    // then
    assertThat(authInfo.getAuthData()).isEqualTo(authInfoMap);
  }

  @Test
  void shouldEncodeDecodeEmptyAuthInfo() {
    // given
    final AuthInfo authInfo = new AuthInfo();

    // when
    encodeDecode(authInfo);

    // then
    assertThat(authInfo.getAuthData()).isEqualTo(Map.of());
  }

  private void encodeDecode(final AuthInfo authInfo) {
    // encode
    final UnsafeBuffer buffer = new UnsafeBuffer(new byte[authInfo.getLength()]);
    authInfo.write(buffer, 0);

    // decode
    authInfo.reset();
    authInfo.wrap(buffer, 0, buffer.capacity());
  }
}
