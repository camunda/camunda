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
        "http://host:9200,"
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
    assertThat(result)
        .isIn("***", "http://****:****@host:9200")
        .satisfies(r -> assertThat(r).doesNotContain(user, password, queryValue));
  }

  @Provide
  Arbitrary<String> secrets() {
    // distinctive prefix so a secret cannot match the fixed parts of the output by chance
    return Arbitraries.strings().all().ofMinLength(1).ofMaxLength(20).map(s -> "Zq9" + s);
  }
}
