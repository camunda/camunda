/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.shared.security.headers;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class SecurityHeadersBaseIT {

  // Common constants
  protected static final String API_TO_VISIT = "v2/authentication/me";
  protected static final String WEB_PAGE_TO_VISIT = "decisions";

  protected static final String NO_CACHE_DIRECTIVE = "no-cache";
  protected static final String CACHE_CONTROL_VALUE =
      "no-cache, no-store, max-age=0, must-revalidate";
  protected static final String PRAGMA_VALUE = NO_CACHE_DIRECTIVE;
  protected static final String EXPIRES_VALUE = "0";
  protected static final String X_CONTENT_TYPE_OPTIONS_VALUE = "nosniff";
  protected static final String X_FRAME_OPTIONS_VALUE = "SAMEORIGIN";
  protected static final String REFERRER_POLICY_VALUE = "strict-origin-when-cross-origin";
  protected static final String CROSS_ORIGIN_OPENER_POLICY_VALUE = "same-origin-allow-popups";
  protected static final String CROSS_ORIGIN_EMBEDDER_POLICY_VALUE = "unsafe-none";
  protected static final String CROSS_ORIGIN_RESOURCE_POLICY_VALUE = "same-site";

  // Common infrastructure
  @Container
  protected static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @AutoClose protected static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  // Abstract methods to be implemented by subclasses
  protected abstract CamundaClient getCamundaClient();

  protected abstract HttpResponse<String> makeAuthenticatedRequest(String path) throws Exception;

  protected abstract void assertSecurityHeaders(Map<String, List<String>> headers);

  // Common utility methods
  protected static URI createUri(final CamundaClient client, final String path)
      throws URISyntaxException {
    return new URI("%s%s".formatted(client.getConfiguration().getRestAddress(), path));
  }

  // Common test methods
  @Test
  void defaultHeadersShouldBeReturnedViaAPIVisit() throws Exception {
    // when
    final HttpResponse<String> response = makeAuthenticatedRequest(API_TO_VISIT);

    // then
    assertSecurityHeaders(response.headers().map());
  }

  @Test
  void defaultHeadersShouldBeReturnedViaPageVisit() throws Exception {
    // when
    final HttpResponse<String> response = makeAuthenticatedRequest(WEB_PAGE_TO_VISIT);

    // then
    assertSecurityHeaders(response.headers().map());
  }
}
