/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import io.camunda.identity.sdk.Identity;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public final class M2MTokenInterceptor implements ClientHttpRequestInterceptor {

  final Identity identity;
  final String audience;

  public M2MTokenInterceptor(final Identity identity, final String audience) {
    this.identity = identity;
    this.audience = audience;
  }

  @Override
  public ClientHttpResponse intercept(
      final HttpRequest request, final byte[] body, final ClientHttpRequestExecution execution)
      throws IOException {
    final String token = identity.authentication().requestToken(audience).getAccessToken();
    final HttpHeaders headers = request.getHeaders();
    headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    return execution.execute(request, body);
  }
}
