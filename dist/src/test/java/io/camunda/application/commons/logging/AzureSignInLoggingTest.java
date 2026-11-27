/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpPipelineBuilder;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.BearerTokenAuthenticationPolicy;
import com.azure.core.util.Configuration;
import com.azure.core.util.ConfigurationBuilder;
import com.azure.core.util.ConfigurationSource;
import com.azure.core.util.Context;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Runs a real Azure sign-in under the shipped {@code log4j2.xml}, through the bearer-token policy
 * and token cache every Azure client signs in with, against an identity endpoint answering with a
 * token response cut off inside the token. The SDK's JSON parser quotes such a response in its
 * error, which the Azure SDK loggers would write to the broker log.
 */
final class AzureSignInLoggingTest {

  private static final String SHIPPED_CONFIG = "src/main/config/log4j2.xml";
  private static final String TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.leaked-access-token";
  private static final String VAULT_SCOPE = "https://vault.azure.net/.default";

  @TempDir private Path appHome;
  private LoggerContext context;
  private URI originalConfig;
  private String originalAppHome;

  @BeforeEach
  void useShippedConfig() {
    originalAppHome = System.getProperty("app.home");
    System.setProperty("app.home", appHome.toString());
    context = (LoggerContext) LogManager.getContext(false);
    originalConfig = context.getConfigLocation();
    context.setConfigLocation(Path.of(SHIPPED_CONFIG).toUri());
  }

  @AfterEach
  void restoreConfig() {
    // a null location makes log4j look its configuration up again, as it did at start-up
    context.setConfigLocation(originalConfig);
    if (originalAppHome == null) {
      System.clearProperty("app.home");
    } else {
      System.setProperty("app.home", originalAppHome);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("credentials")
  void shouldNotWriteTheTokenOfAnUnparsableSignInResponseToTheBrokerLog(
      final String name, final Function<HttpClient, TokenCredential> credential) throws Exception {
    // given an Azure client's pipeline, whose bearer-token policy signs in through its token cache
    final var http = new TruncatedTokenResponses();
    final var client =
        new HttpPipelineBuilder()
            .httpClient(http)
            .policies(new BearerTokenAuthenticationPolicy(credential.apply(http), VAULT_SCOPE))
            .build();

    // when
    assertThatThrownBy(() -> client.sendSync(secretRequest(), Context.NONE))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> client.send(secretRequest()).block())
        .isInstanceOf(RuntimeException.class);
    LogManager.getLogger("io.camunda.test").warn("canary");

    // then
    final var log = Files.readString(appHome.resolve("logs/zeebe.log"));
    assertThat(log).contains("canary").doesNotContain(TOKEN);
  }

  private static HttpRequest secretRequest() {
    return new HttpRequest(HttpMethod.GET, "https://my-vault.vault.azure.net/secrets/db-password");
  }

  static Stream<Arguments> credentials() {
    // isolated from the CI host's AZURE_* and IDENTITY_* variables
    final ConfigurationSource empty = path -> Map.of();
    final Configuration environment = new ConfigurationBuilder(empty, empty, empty).build();
    return Stream.of(
        Arguments.of(
            "managed identity",
            (Function<HttpClient, TokenCredential>)
                http ->
                    new ManagedIdentityCredentialBuilder()
                        .configuration(environment)
                        .httpClient(http)
                        .build()),
        Arguments.of(
            "service principal",
            (Function<HttpClient, TokenCredential>)
                http ->
                    new ClientSecretCredentialBuilder()
                        .configuration(environment)
                        .tenantId("tenant-id")
                        .clientId("client-id")
                        .clientSecret("client-secret")
                        .httpClient(http)
                        .build()));
  }

  /** Answers every token request, to Entra ID and IMDS alike, with a cut-off token response. */
  private static final class TruncatedTokenResponses implements HttpClient {

    @Override
    public Mono<HttpResponse> send(final HttpRequest request) {
      final var url = request.getUrl().toString();
      if (url.contains("/discovery/instance")) {
        final var host = request.getUrl().getHost();
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
      if (url.contains("/oauth2/v2.0/token") || url.contains("/metadata/identity/oauth2/token")) {
        return json(
            request,
            200,
            "{\"token_type\":\"Bearer\",\"expires_in\":3600,\"access_token\":\"" + TOKEN);
      }
      return json(request, 404, "{}");
    }

    private static Mono<HttpResponse> json(
        final HttpRequest request, final int status, final String body) {
      return Mono.just(new JsonResponse(request, status, body));
    }
  }

  private static final class JsonResponse extends HttpResponse {

    private final int status;
    private final byte[] body;

    private JsonResponse(final HttpRequest request, final int status, final String body) {
      super(request);
      this.status = status;
      this.body = body.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public int getStatusCode() {
      return status;
    }

    @Deprecated
    @Override
    public String getHeaderValue(final String name) {
      return getHeaders().getValue(HttpHeaderName.fromString(name));
    }

    @Override
    public HttpHeaders getHeaders() {
      return new HttpHeaders().set(HttpHeaderName.CONTENT_TYPE, "application/json");
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
