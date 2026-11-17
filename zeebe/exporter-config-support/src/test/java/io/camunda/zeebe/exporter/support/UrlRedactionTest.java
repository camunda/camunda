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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class UrlRedactionTest {

  @Test
  void shouldMaskUserInfo() {
    // when
    final var result = UrlRedaction.describe("https://elastic:s3cr3t@host:9200");

    // then
    assertThat(result).isEqualTo("https://****:****@host:9200");
  }

  @Test
  void shouldDescribeEveryUrlInList() {
    // when
    final var result =
        UrlRedaction.describe("http://a:s3cr3t1@one:9200,http://two:9200,https://three");

    // then
    assertThat(result).isEqualTo("http://****:****@one:9200,http://two:9200,https://three");
  }

  @Test
  void shouldKeepUrlWithoutUserInfo() {
    // when
    final var result = UrlRedaction.describe("http://localhost:9200");

    // then
    assertThat(result).isEqualTo("http://localhost:9200");
  }

  @Test
  void shouldDropPathQueryAndFragment() {
    // when
    final var result =
        UrlRedaction.describe("http://localhost:9200/p/ath?api_key=s3cr3t&flag#frag");

    // then
    assertThat(result).isEqualTo("http://localhost:9200");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // comma inside the password splits it into unparseable segments
        "http://user:sec,ret@host:9200",
        // missing scheme
        "user:secret@host:9200",
        // malformed scheme separator
        "http:/user:secret@host:9200",
        // space is illegal in a URI
        "http://user:s3 cr3t@host:9200",
        // non numeric port
        "http://user:secret@host:notaport",
        // empty segment
        "http://host:9200,",
        // '/' in the password ends the authority early, host and port would be the credentials
        "http://private-user:12345/secret@host:9200",
        "http://alice/name:password@host:9200",
        // '?' and '#' in the password end the authority early
        "http://user:pass?word@host:9200",
        "http://user:pass#word@host:9200",
        // '@' in the user plus '/' in the password leaves a valid host and user info behind
        "http://x@a:/secret@host:9200"
      })
  void shouldOmitUnparseableUrl(final String url) {
    assertThat(UrlRedaction.describe(url)).isEqualTo("***");
  }

  @Test
  void shouldKeepNull() {
    assertThat(UrlRedaction.describe(null)).isNull();
  }

  @Property
  void shouldNeverExposeCredentials(
      @ForAll("secrets") final String user,
      @ForAll("secrets") final String password,
      @ForAll("secrets") final String queryValue) {
    // given
    final var url = "http://" + user + ":" + password + "@host:9200?k=" + queryValue;

    // when
    final var result = UrlRedaction.describe(url);

    // then
    assertThat(result).isIn("***", "http://****:****@host:9200");
  }

  @Provide
  Arbitrary<String> secrets() {
    // URI delimiters and digits are over-represented, as they are what shifts the authority
    // boundary and lets a numeric port parse out of a password
    return Arbitraries.strings()
        .withChars("abcXYZ0123456789/?#@:%[]-._~")
        .ofMinLength(1)
        .ofMaxLength(20);
  }
}
