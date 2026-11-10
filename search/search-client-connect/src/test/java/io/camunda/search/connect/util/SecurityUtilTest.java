/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hc.core5.http.Header;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class SecurityUtilTest {

  @Test
  void shouldBuildBasicAuthorizationHeader() {
    // when
    final var header = SecurityUtil.basicAuthorizationHeader("someUser", "somePassword");

    // then
    assertThat(header)
        .get()
        .extracting(Header::getName, Header::getValue)
        .containsExactly("Authorization", "Basic c29tZVVzZXI6c29tZVBhc3N3b3Jk");
  }

  @ParameterizedTest
  @CsvSource(
      value = {"null,null", "someUser,null", "null,somePassword", "'',somePassword", "someUser,''"},
      nullValues = "null")
  void shouldNotBuildAuthorizationHeaderWithoutBothUsernameAndPassword(
      final String username, final String password) {
    // when
    final var header = SecurityUtil.basicAuthorizationHeader(username, password);

    // then
    assertThat(header).isEmpty();
  }
}
