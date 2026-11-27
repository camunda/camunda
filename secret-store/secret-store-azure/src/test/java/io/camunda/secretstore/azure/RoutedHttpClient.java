/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.core.util.Configuration;
import com.azure.core.util.Context;
import io.netty.handler.ssl.SslContextBuilder;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLException;
import reactor.core.publisher.Mono;

/**
 * The SDK's own Netty HTTP client, trusting only the {@link TestCertificate}, with every request to
 * Azure sent to the {@link MockAzure} endpoints instead.
 *
 * <p>The SDK's pipeline keeps Azure's host names, so its checks on them run as in production: Key
 * Vault's challenge policy, for example, accepts only a vault host under the challenge's resource
 * ({@code *.vault.azure.net}). On the wire, requests go to {@code localhost}, so the Host header,
 * SNI and hostname verification use it. The original host travels in {@link #ORIGINAL_HOST}, for
 * the mocks to match on. A host the mocks don't serve fails the test at once, with an error the SDK
 * doesn't retry, so no test ever reaches the internet.
 */
final class RoutedHttpClient implements HttpClient {

  static final String ORIGINAL_HOST = "X-Test-Original-Host";
  static final String VAULT_DOMAIN = ".vault.azure.net";

  /**
   * Not the SDK's default authority ({@code login.microsoftonline.com}), so a token request
   * reaching it proves the store's {@code authority-host} setting is used.
   */
  static final String AUTHORITY_HOST = "login.microsoftonline.us";

  static final String IMDS_HOST = "169.254.169.254";

  private final HttpClient netty;
  private final int httpsPort;
  private final int httpPort;

  RoutedHttpClient(final X509Certificate trusted, final int httpsPort, final int httpPort) {
    this.httpsPort = httpsPort;
    this.httpPort = httpPort;
    try {
      final var sslContext = SslContextBuilder.forClient().trustManager(trusted).build();
      netty =
          new NettyAsyncHttpClientBuilder(
                  reactor.netty.http.client.HttpClient.create()
                      .secure(spec -> spec.sslContext(sslContext)))
              // no proxy from this machine's environment
              .configuration(Configuration.NONE)
              .build();
    } catch (final SSLException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public Mono<HttpResponse> send(final HttpRequest request) {
    return send(request, Context.NONE);
  }

  @Override
  public Mono<HttpResponse> send(final HttpRequest request, final Context context) {
    return Mono.defer(() -> netty.send(route(request), context));
  }

  @Override
  public HttpResponse sendSync(final HttpRequest request, final Context context) {
    return netty.sendSync(route(request), context);
  }

  private HttpRequest route(final HttpRequest request) {
    final var url = request.getUrl();
    final var host = url.getHost();
    final String target;
    if (host.endsWith(VAULT_DOMAIN) || host.equals(AUTHORITY_HOST)) {
      target = "https://localhost:" + httpsPort;
    } else if (host.equals(IMDS_HOST)) {
      target = "http://localhost:" + httpPort;
    } else {
      // an Error, as azure-core's RetryPolicy retries every Exception with backoff
      throw new AssertionError("Not routed to a mock, refusing to connect: " + host);
    }
    return request
        .copy()
        .setUrl(rewrite(url, target))
        .setHeader(HttpHeaderName.fromString(ORIGINAL_HOST), host);
  }

  private static URL rewrite(final URL url, final String target) {
    try {
      final var path = url.getFile().isEmpty() ? "/" : url.getFile();
      return new URI(target + path).toURL();
    } catch (final URISyntaxException | MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
