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
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.util.Configuration;
import com.azure.core.util.ConfigurationBuilder;
import com.azure.core.util.ConfigurationSource;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Stands in for Microsoft Entra ID, IMDS and Key Vault: records every request the SDK sends and
 * answers it from a routing function, so tests can see which identity and authority a credential
 * signs in with without any network.
 */
final class StubAzureHttpClient implements HttpClient {

  static final String TOKEN = "stub-access-token";

  private static final Pattern SECRET_PATH = Pattern.compile("/secrets/([^/?]+)(?:/[^/?]*)?");

  private final List<Request> requests = new CopyOnWriteArrayList<>();
  // keyed by lower-cased name, as Key Vault compares names case-insensitively
  private final Map<String, String> secrets = new ConcurrentHashMap<>();
  private volatile boolean holdingSecrets;
  private final Function<Request, Mono<HttpResponse>> router;
  private final AtomicBoolean rejectingSignIn;
  private final AtomicInteger vaultStatus;

  private StubAzureHttpClient(final Function<Request, Mono<HttpResponse>> router) {
    this(router, new AtomicBoolean(), new AtomicInteger());
  }

  private StubAzureHttpClient(
      final Function<Request, Mono<HttpResponse>> router,
      final AtomicBoolean rejectingSignIn,
      final AtomicInteger vaultStatus) {
    this.router = router;
    this.rejectingSignIn = rejectingSignIn;
    this.vaultStatus = vaultStatus;
  }

  /**
   * Answers Entra ID and IMDS with a token valid for {@code tokenLifetimeSeconds}, and the vault,
   * until {@link #rejectSignIn(boolean) told} to reject sign-in or {@link #answerVaultWith(int) to
   * refuse} the vault's calls.
   */
  static StubAzureHttpClient signingIn(final long tokenLifetimeSeconds) {
    final var rejecting = new AtomicBoolean();
    final var vaultStatus = new AtomicInteger();
    return new StubAzureHttpClient(
        request -> {
          if (rejecting.get() && request.isTokenRequest()) {
            return Mono.just(invalidClient(request));
          }
          if (vaultStatus.get() != 0 && request.isAuthorizedVaultRequest()) {
            return Mono.just(vaultError(request, vaultStatus.get()));
          }
          return Mono.just(answer(request, tokenLifetimeSeconds));
        },
        rejecting,
        vaultStatus);
  }

  /** Refuses every connection, as on a machine with no IMDS and no network route to Azure. */
  static StubAzureHttpClient offline() {
    return new StubAzureHttpClient(
        request -> Mono.error(new ConnectException("Connection refused: " + request.host())));
  }

  /** Rejects every sign-in with {@code invalid_client}, as Entra ID does for a wrong secret. */
  static StubAzureHttpClient rejecting() {
    return new StubAzureHttpClient(request -> Mono.just(invalidClient(request)));
  }

  /** Answers every sign-in, to Entra ID and IMDS alike, with {@code status} and {@code body}. */
  static StubAzureHttpClient answeringSignIn(final int status, final String body) {
    return new StubAzureHttpClient(
        request ->
            Mono.just(
                request.isTokenRequest() ? json(request, status, body) : answer(request, 3600)));
  }

  /** From now on rejects, or again answers, every sign-in of a {@link #signingIn} stub. */
  void rejectSignIn(final boolean reject) {
    rejectingSignIn.set(reject);
  }

  /**
   * From now on answers every signed-in call to the vault of a {@link #signingIn} stub with {@code
   * status} and Key Vault's error body for it, or, with {@code 0}, again as usual.
   */
  void answerVaultWith(final int status) {
    vaultStatus.set(status);
  }

  /**
   * Stores {@code value} as the latest version of the secret {@code name}. From the first call on,
   * the vault holds only the secrets put, answering any other name with 404, rather than one
   * placeholder value for every name.
   */
  StubAzureHttpClient putSecret(final String name, final String value) {
    secrets.put(name.toLowerCase(Locale.ROOT), value);
    holdingSecrets = true;
    return this;
  }

  /** An environment holding only {@code variables}, isolated from this process's. */
  static Configuration environment(final Map<String, String> variables) {
    final ConfigurationSource source = path -> path == null ? variables : Map.of();
    return new ConfigurationBuilder(source, source, source).build();
  }

  @Override
  public Mono<HttpResponse> send(final HttpRequest request) {
    final var body = request.getBodyAsBinaryData();
    final var recorded =
        new Request(
            request,
            request.getHttpMethod().name(),
            request.getUrl().toString(),
            body == null ? "" : body.toString(),
            request.getHeaders().getValue(HttpHeaderName.AUTHORIZATION));
    requests.add(recorded);
    if (holdingSecrets && vaultStatus.get() == 0 && recorded.isAuthorizedVaultRequest()) {
      final var secretName = recorded.secretName();
      if (secretName != null) {
        return Mono.just(secret(recorded, secretName));
      }
    }
    return router.apply(recorded);
  }

  List<Request> requests() {
    return List.copyOf(requests);
  }

  List<Request> tokenRequests() {
    return requests.stream().filter(Request::isTokenRequest).toList();
  }

  private static HttpResponse answer(final Request request, final long tokenLifetimeSeconds) {
    if (request.url().contains("/discovery/instance")) {
      final var host = request.host();
      return json(
          request,
          200,
          """
          {"tenant_discovery_endpoint":"https://%1$s/common/v2.0/.well-known/openid-configuration",
           "api-version":"1.1",
           "metadata":[{"preferred_network":"%1$s","preferred_cache":"%1$s","aliases":["%1$s"]}]}
          """
              .formatted(host));
    }
    if (request.isImdsRequest()) {
      final long expiresOn = Instant.now().getEpochSecond() + tokenLifetimeSeconds;
      return json(
          request,
          200,
          """
          {"access_token":"%s","expires_in":"%d","expires_on":"%d",
           "resource":"https://vault.azure.net","token_type":"Bearer"}
          """
              .formatted(TOKEN, tokenLifetimeSeconds, expiresOn));
    }
    if (request.isTokenRequest()) {
      return json(
          request,
          200,
          """
          {"token_type":"Bearer","expires_in":%1$d,"ext_expires_in":%1$d,"access_token":"%2$s"}
          """
              .formatted(tokenLifetimeSeconds, TOKEN));
    }
    if (request.authorization() == null) {
      // Key Vault answers an unauthenticated request with the challenge the SDK signs in for
      final var headers =
          new HttpHeaders()
              .set(
                  HttpHeaderName.WWW_AUTHENTICATE,
                  "Bearer authorization=\"https://login.microsoftonline.com/tenant-id\","
                      + " resource=\"https://vault.azure.net\"");
      return new StubResponse(request.original(), 401, headers, "");
    }
    if (request.url().replaceAll("\\?.*", "").endsWith("/secrets")) {
      return json(
          request,
          200,
          """
          {"value":[],"nextLink":null}
          """);
    }
    return json(
        request,
        200,
        """
        {"value":"secret-value","id":"%s/1","attributes":{"enabled":true}}
        """
            .formatted(request.url().replaceAll("\\?.*", "")));
  }

  private HttpResponse secret(final Request request, final String name) {
    final var value = secrets.get(name.toLowerCase(Locale.ROOT));
    if (value == null) {
      return json(
          request,
          404,
          """
          {"error":{"code":"SecretNotFound","message":"A secret with (name/id) %s was not found in this key vault."}}
          """
              .formatted(name));
    }
    return json(
        request,
        200,
        """
        {"value":"%s","id":"https://%s/secrets/%s/1","attributes":{"enabled":true}}
        """
            .formatted(jsonEscape(value), request.host(), name));
  }

  private static String jsonEscape(final String value) {
    final var escaped = new StringBuilder(value.length());
    for (final char c : value.toCharArray()) {
      if (c == '"' || c == '\\') {
        escaped.append('\\').append(c);
      } else if (c < 0x20) {
        escaped.append("\\u%04x".formatted((int) c));
      } else {
        escaped.append(c);
      }
    }
    return escaped.toString();
  }

  private static HttpResponse invalidClient(final Request request) {
    return json(
        request,
        401,
        """
        {"error":"invalid_client","error_description":"AADSTS7000215: Invalid client secret provided."}
        """);
  }

  private static HttpResponse vaultError(final Request request, final int status) {
    final var code = status == 403 ? "Forbidden" : status == 404 ? "SecretNotFound" : "Error";
    return json(
        request,
        status,
        """
        {"error":{"code":"%s","message":"Key Vault answered %d"}}
        """
            .formatted(code, status));
  }

  private static HttpResponse json(final Request request, final int status, final String body) {
    return new StubResponse(
        request.original(),
        status,
        new HttpHeaders().set(HttpHeaderName.CONTENT_TYPE, "application/json"),
        body);
  }

  record Request(
      HttpRequest original, String method, String url, String body, String authorization) {

    String host() {
      return original.getUrl().getHost();
    }

    boolean isImdsRequest() {
      return url.contains("/metadata/identity/oauth2/token");
    }

    boolean isTokenRequest() {
      return isImdsRequest() || url.contains("/oauth2/v2.0/token");
    }

    boolean isAuthorizedVaultRequest() {
      return !isTokenRequest() && authorization != null;
    }

    /** The secret a {@code getSecret} request reads, or {@code null} for any other request. */
    @Nullable String secretName() {
      final var matcher = SECRET_PATH.matcher(original.getUrl().getPath());
      return "GET".equals(method) && matcher.matches() ? matcher.group(1) : null;
    }
  }

  private static final class StubResponse extends HttpResponse {

    private final int status;
    private final HttpHeaders headers;
    private final byte[] body;

    private StubResponse(
        final HttpRequest request, final int status, final HttpHeaders headers, final String body) {
      super(request);
      this.status = status;
      this.headers = headers;
      this.body = body.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public int getStatusCode() {
      return status;
    }

    @Deprecated
    @Override
    public String getHeaderValue(final String name) {
      return headers.getValue(HttpHeaderName.fromString(name));
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }

    @Override
    public Flux<ByteBuffer> getBody() {
      return Flux.just(ByteBuffer.wrap(body));
    }

    @Override
    public Mono<byte[]> getBodyAsByteArray() {
      return Mono.just(body);
    }

    @Override
    public Mono<String> getBodyAsString() {
      return Mono.just(new String(body, StandardCharsets.UTF_8));
    }

    @Override
    public Mono<String> getBodyAsString(final Charset charset) {
      return Mono.just(new String(body, charset));
    }
  }
}
