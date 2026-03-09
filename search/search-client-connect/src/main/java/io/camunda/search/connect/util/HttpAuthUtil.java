/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.HttpRequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for configuring preemptive HTTP Basic authentication on Apache HttpClient 5 async
 * builders used by OpenSearch (and Elasticsearch) connectors.
 *
 * <h2>Background</h2>
 *
 * <p>The opensearch-java client (3.4.0) delegates preemptive auth to its internal {@code
 * WrappingAuthCache}, which participates in a multi-step pipeline inside Apache HttpClient 5's
 * async execution chain. That pipeline involves:
 *
 * <ol>
 *   <li>{@code AsyncProtocolExec.execute()} invoking {@code AuthCacheKeeper.loadPreemptively(host,
 *       pathPrefix, authExchange, context)}
 *   <li>{@code WrappingAuthCache.get(host)} looking up the cached {@code BasicScheme}, retrieving
 *       credentials via {@code context.getCredentialsProvider().getCredentials(authScope,
 *       context)}, and calling {@code BasicScheme.initPreemptive(credentials)}
 *   <li>{@code AsyncProtocolExec.internalExecute()} checking for an existing {@code Authorization}
 *       header, and if absent, calling {@code AuthenticationHandler.addAuthResponse()} which
 *       delegates to {@code BasicScheme.generateAuthResponse()}
 * </ol>
 *
 * <p>If any step in this pipeline fails to produce credentials (e.g. {@code getCredentials()}
 * returns {@code null}), then {@code BasicScheme.generateAuthResponse()} throws an {@code
 * AuthenticationException("User credentials not set")}. That exception is caught and logged at
 * ERROR level inside {@code AuthenticationHandler.addAuthResponse()}, but the request proceeds
 * <em>without</em> an {@code Authorization} header.
 *
 * <h2>Why a missing header is fatal</h2>
 *
 * <p>The opensearch-java transport treats HTTP 401 responses as terminal errors. In {@code
 * ApacheHttpClient5Transport.prepareResponse()}, a 401 status immediately throws a {@code
 * TransportException("Unauthorized access")} with no retry and no challenge-response negotiation.
 * Additionally, automatic retries are disabled via {@code disableAutomaticRetries()} in {@code
 * ApacheHttpClient5TransportBuilder.build()}. A single missing {@code Authorization} header
 * therefore causes an unrecoverable authentication failure.
 *
 * <p>This is particularly problematic with AWS OpenSearch Service using fine-grained access control
 * (FGAC) with the internal user database, where the security plugin returns 401 with {@code
 * "Authentication finally failed"} when no credentials are presented.
 *
 * <h2>The fix</h2>
 *
 * <p>This utility adds a request interceptor via {@code
 * HttpAsyncClientBuilder.addRequestInterceptorFirst()} that directly sets the {@code Authorization:
 * Basic} header on every outgoing request. Because request interceptors registered this way run
 * inside the {@code HttpProcessor} chain in {@code HttpAsyncMainClientExec} (which executes before
 * {@code AsyncProtocolExec} checks for the header), the {@code Authorization} header is guaranteed
 * to be present regardless of whether the standard auth cache pipeline succeeds. This is the same
 * pattern used by {@code addPreemptiveProxyAuthInterceptor} for proxy authentication throughout the
 * codebase.
 *
 * @see <a href="https://docs.aws.amazon.com/opensearch-service/latest/developerguide/fgac.html">AWS
 *     OpenSearch Fine-Grained Access Control</a>
 */
public final class HttpAuthUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpAuthUtil.class);

  private HttpAuthUtil() {}

  /**
   * Adds a request interceptor to the given {@link HttpAsyncClientBuilder} that sets the {@code
   * Authorization: Basic} header on every outgoing request if one is not already present.
   *
   * <p>This should be called in addition to (not instead of) setting a {@link
   * org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider} on the builder, so that the
   * standard auth pipeline is still available as a fallback.
   *
   * @param builder the HTTP async client builder to configure
   * @param username the basic auth username
   * @param password the basic auth password
   */
  public static void addPreemptiveBasicAuthInterceptor(
      final HttpAsyncClientBuilder builder, final String username, final String password) {
    final String credentials = username + ":" + password;
    final String encodedCredentials =
        Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    final String basicAuthHeaderValue = "Basic " + encodedCredentials;

    builder.addRequestInterceptorFirst(
        (HttpRequestInterceptor)
            (request, entity, context) -> {
              if (!request.containsHeader("Authorization")) {
                request.addHeader("Authorization", basicAuthHeaderValue);
              }
            });

    LOGGER.debug("Preemptive basic authentication enabled for search engine connection");
  }
}
