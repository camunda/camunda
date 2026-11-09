/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.es;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import org.junit.jupiter.api.Test;

class ElasticsearchCompatibilityInterceptorTest {

  private final ElasticsearchCompatibilityInterceptor interceptor =
      ElasticsearchCompatibilityInterceptor.INSTANCE;

  @Test
  void shouldRewriteAcceptAndContentTypeToVersion8() {
    // given
    final var request = new BasicHttpRequest("POST", "/_bulk");
    request.setHeader(HttpHeaders.ACCEPT, "application/vnd.elasticsearch+json; compatible-with=9");
    request.setHeader(
        HttpHeaders.CONTENT_TYPE, "application/vnd.elasticsearch+json; compatible-with=9");

    // when
    interceptor.process(request, null, HttpCoreContext.create());

    // then
    assertThat(request.getFirstHeader(HttpHeaders.ACCEPT).getValue())
        .isEqualTo("application/vnd.elasticsearch+json; compatible-with=8");
    assertThat(request.getFirstHeader(HttpHeaders.CONTENT_TYPE).getValue())
        .isEqualTo("application/vnd.elasticsearch+json; compatible-with=8");
  }

  @Test
  void shouldLeaveOtherHeadersUntouched() {
    // given
    final var request = new BasicHttpRequest("GET", "/");
    request.setHeader(HttpHeaders.ACCEPT, "application/json");

    // when
    interceptor.process(request, null, HttpCoreContext.create());

    // then
    assertThat(request.getFirstHeader(HttpHeaders.ACCEPT).getValue()).isEqualTo("application/json");
    assertThat(request.getFirstHeader(HttpHeaders.CONTENT_TYPE)).isNull();
  }
}
