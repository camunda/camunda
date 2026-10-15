/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.support;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

final class UrlRedactionTest {

  @Test
  void shouldRedactUserInfo() {
    // when
    final var result = UrlRedaction.redactUserInfo("https://elastic:s3cr3t@host:9200");

    // then
    assertThat(result).isEqualTo("https://***@host:9200");
  }

  @Test
  void shouldRedactUserInfoOfEveryUrlInList() {
    // when
    final var result =
        UrlRedaction.redactUserInfo("http://a:s3cr3t1@one:9200,http://b:s3cr3t2@two:9200");

    // then
    assertThat(result).isEqualTo("http://***@one:9200,http://***@two:9200");
  }

  @Test
  void shouldRedactPasswordContainingSlash() {
    // when
    final var result = UrlRedaction.redactUserInfo("http://user:pa/ss@host:9200");

    // then
    assertThat(result).doesNotContain("pa/ss").doesNotContain("user");
  }

  @Test
  void shouldRedactPasswordContainingWhitespace() {
    // when
    final var result = UrlRedaction.redactUserInfo("http://user:s3 cr3t@host:9200");

    // then
    assertThat(result).isEqualTo("http://***@host:9200");
  }

  @Test
  void shouldKeepUrlWithoutUserInfo() {
    // when
    final var result = UrlRedaction.redactUserInfo("http://localhost:9200");

    // then
    assertThat(result).isEqualTo("http://localhost:9200");
  }

  @Test
  void shouldKeepNull() {
    assertThat(UrlRedaction.redactUserInfo(null)).isNull();
  }

  @Property
  void shouldNeverKeepCredentialsOfAnyUrl(
      @ForAll("userInfos") final String userInfo, @ForAll("hostParts") final String hostPart) {
    // given
    final var url = "http://" + userInfo + "@" + hostPart;

    // when
    final var result = UrlRedaction.redactUserInfo(url);

    // then
    assertThat(result).isEqualTo("http://***@" + hostPart);
  }

  @Property
  void shouldNeverKeepCredentialsInUrlList(
      @ForAll("userInfos") final String first,
      @ForAll("userInfos") final String second,
      @ForAll("hostParts") final String hostPart) {
    // given
    final var urls = "https://" + first + "@one," + "http://" + second + "@" + hostPart;

    // when
    final var result = UrlRedaction.redactUserInfo(urls);

    // then
    assertThat(result).isEqualTo("https://***@one,http://***@" + hostPart);
  }

  @Provide
  Arbitrary<String> userInfos() {
    // any char but ',' (the url separator) and '@' ending the userinfo; covers '/', ':', spaces
    return Arbitraries.strings().ofMinLength(1).ofMaxLength(30).excludeChars(',', '@');
  }

  @Provide
  Arbitrary<String> hostParts() {
    return Arbitraries.strings()
        .alpha()
        .numeric()
        .ofMinLength(1)
        .ofMaxLength(15)
        .map(h -> h + ":9200");
  }
}
