/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.api.response.Topology;
import io.camunda.client.impl.CamundaClientCredentials;
import io.camunda.client.impl.oauth.OAuthCredentialsCache;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.TopologyResponse;
import io.camunda.client.util.RecordingGatewayService;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.TestCredentialsApplier;
import io.camunda.client.util.TestCredentialsApplier.Credential;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.net.ssl.SSLHandshakeException;
import org.apache.hc.core5.http.HttpHeaders;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

@WireMockTest
public final class OAuthCredentialsProviderTest {

  private static final String VALID_TRUSTSTORE_PATH =
      OAuthCredentialsProviderTest.class
          .getClassLoader()
          .getResource("idp-ssl/truststore.jks")
          .getPath();

  private static final String TAMPERED_TRUSTSTORE_PATH =
      OAuthCredentialsProviderTest.class
          .getClassLoader()
          .getResource("idp-ssl/untruststore.jks")
          .getPath();

  private static final String VALID_IDENTITY_PATH =
      OAuthCredentialsProviderTest.class
          .getClassLoader()
          .getResource("idp-ssl/identity.p12")
          .getPath();

  private static final String VALID_CLIENT_PATH =
      OAuthCredentialsProviderTest.class
          .getClassLoader()
          .getResource("idp-ssl/localhost.p12")
          .getPath();

  private static final String OAUTH_SSL_CLIENT_CERT_PATH =
      OAuthCredentialsProviderTest.class.getClassLoader().getResource("oauth/test.jks").getPath();

  private static final String TRUSTSTORE_PASSWORD = "password";
  private static final String KEYSTORE_PASSWORD = "password";

  @RegisterExtension
  static WireMockExtension httpsWiremock =
      WireMockExtension.newInstance()
          .options(
              WireMockConfiguration.wireMockConfig()
                  .dynamicPort()
                  .dynamicHttpsPort()
                  .trustStorePath(VALID_TRUSTSTORE_PATH)
                  .trustStorePassword(TRUSTSTORE_PASSWORD)
                  .needClientAuth(true)
                  .keystorePath(VALID_IDENTITY_PATH)
                  .keystorePassword(KEYSTORE_PASSWORD))
          .build();

  private static final String OAUTH_SSL_CLIENT_CERT_PASSWORD = "mstest";
  private static final String KEYSTORE_MATERIAL_PASSWORD = "password";
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final Key<String> AUTH_KEY =
      Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  private static final ZonedDateTime EXPIRY =
      ZonedDateTime.of(3020, 1, 1, 0, 0, 0, 0, ZoneId.of("Z"));
  private static final String SECRET = "secret";
  private static final String JWT_ASSERTION_TYPE =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
  private static final String AUDIENCE = "endpoint";
  private static final String SCOPE = "721aa3ee-24c9-4ab5-95bc-d921ecafdd6d/.default";
  private static final String ACCESS_TOKEN = "someToken";
  private static final String TOKEN_TYPE = "Bearer";
  private static final String CLIENT_ID = "client";
  private final TestCredentialsApplier applier = new TestCredentialsApplier();
  private final WireMockRuntimeInfo defaultWiremockRuntimeInfo;

  private WireMockRuntimeInfo currentWiremockRuntimeInfo;

  private Path cacheFilePath;
  private final ObjectMapper jsonMapper = new ObjectMapper();

  public OAuthCredentialsProviderTest(final WireMockRuntimeInfo defaultWiremockRuntimeInfo) {
    this.defaultWiremockRuntimeInfo = defaultWiremockRuntimeInfo;
  }

  @BeforeEach
  void beforeEach(final @TempDir Path tmpDir) throws IOException {
    cacheFilePath = Files.createFile(tmpDir.resolve("cache"));
    currentWiremockRuntimeInfo = defaultWiremockRuntimeInfo;
  }

  @Test
  void shouldRequestTokenAndAddToCall() throws IOException {
    // given
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(tokenUrlString())
            .credentialsCachePath(cacheFilePath.toString())
            .build();
    mockCredentials(ACCESS_TOKEN, null);

    // when
    provider.applyCredentials(applier);

    // then
    assertThat(applier.getCredentials())
        .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
  }

  @Test
  void shouldRequestTokenWithScopeAndAddToCall() throws IOException {
    // given
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .audience(AUDIENCE)
            .scope(SCOPE)
            .authorizationServerUrl(tokenUrlString())
            .credentialsCachePath(cacheFilePath.toString())
            .build();
    mockCredentials(ACCESS_TOKEN, SCOPE);

    // when
    provider.applyCredentials(applier);

    // then
    assertThat(applier.getCredentials())
        .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
  }

  @Test
  void shouldRequestTokenWithEmptyScopeAndAddToCall() throws IOException {
    // given
    final String scope = "";
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .audience(AUDIENCE)
            .scope(scope)
            .authorizationServerUrl(tokenUrlString())
            .credentialsCachePath(cacheFilePath.toString())
            .build();
    mockCredentials(ACCESS_TOKEN, scope);

    // when
    provider.applyCredentials(applier);

    // then
    assertThat(applier.getCredentials())
        .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
  }

  @Test
  void shouldRefreshCredentialsOnRetry() throws IOException {
    // given
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(tokenUrlString())
            .credentialsCachePath(cacheFilePath.toString())
            .build();
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFilePath.toFile());
    final TestStatusCode statusCode = new TestStatusCode(0, true);

    // when
    // first, fill in cache, then change mapping, and shouldRetryRequest should also refresh the
    // token
    mockCredentials(ACCESS_TOKEN, null);
    provider.applyCredentials(applier);

    mockCredentials("foo", null);
    final boolean shouldRetry = provider.shouldRetryRequest(statusCode);

    // then
    assertThat(shouldRetry).isTrue();
    assertThat(cache.readCache().get(CLIENT_ID))
        .get()
        .returns("foo", CamundaClientCredentials::getAccessToken);
  }

  @Test
  void shouldNotRetryWithSameCredentials() throws IOException {
    // given
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(tokenUrlString())
            .credentialsCachePath(cacheFilePath.toString())
            .build();
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFilePath.toFile());
    final TestStatusCode statusCode = new TestStatusCode(0, true);

    // when
    // first, fill in cache, then change mapping, and shouldRetryRequest should also refresh the
    // token
    mockCredentials(ACCESS_TOKEN, null);
    provider.applyCredentials(applier);
    final boolean shouldRetry = provider.shouldRetryRequest(statusCode);

    // then - ensure we did request twice (once applyCredentials, once shouldRetryRequest), but
    // still return false since the credentials are the same
    assertThat(shouldRetry).isFalse();
    currentWiremockRuntimeInfo.getWireMock().verifyThat(2, RequestPatternBuilder.allRequests());
  }

  @Test
  void shouldUseCachedCredentials() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFilePath.toFile());
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(tokenUrlString())
            .credentialsCachePath(cacheFilePath.toString())
            .build();
    mockCredentials(ACCESS_TOKEN, null);
    cache
        .put(CLIENT_ID, new CamundaClientCredentials(ACCESS_TOKEN, EXPIRY, TOKEN_TYPE))
        .writeCache();

    // when - should not make any request, but use the cached credentials
    provider.applyCredentials(applier);

    // then
    currentWiremockRuntimeInfo.getWireMock().verifyThat(0, RequestPatternBuilder.allRequests());
  }

  @Test
  void shouldCacheAndReuseCredentials() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFilePath.toFile());
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(tokenUrlString())
            .credentialsCachePath(cacheFilePath.toString())
            .build();
    mockCredentials(ACCESS_TOKEN, null);

    // when - should only request once even when called multiple times
    provider.applyCredentials(applier);
    provider.applyCredentials(applier);
    provider.applyCredentials(applier);

    // then
    currentWiremockRuntimeInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
    assertThat(cache.readCache().get(CLIENT_ID))
        .hasValue(new CamundaClientCredentials(ACCESS_TOKEN, EXPIRY, TOKEN_TYPE));
  }

  @Test
  void shouldUpdateCacheIfRetried() throws IOException {
    // given
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFilePath.toFile());
    final StatusCode unauthorizedCode = new TestStatusCode(0, true);
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(tokenUrlString())
            .credentialsCachePath(cacheFilePath.toString())
            .build();
    mockCredentials(ACCESS_TOKEN, null);
    cache.put(CLIENT_ID, new CamundaClientCredentials("invalid", EXPIRY, TOKEN_TYPE)).writeCache();

    // when - should refresh on unauthorized and write new token
    provider.shouldRetryRequest(unauthorizedCode);

    // then
    currentWiremockRuntimeInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
    assertThat(cache.readCache().get(CLIENT_ID))
        .hasValue(new CamundaClientCredentials(ACCESS_TOKEN, EXPIRY, TOKEN_TYPE));
  }

  @Test
  void shouldThrowExceptionOnTimeout() {
    // given
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl(tokenUrlString())
            .credentialsCachePath(cacheFilePath.toString())
            .readTimeout(Duration.ofMillis(500))
            // disable retries: this test asserts the read-timeout behavior of a single fetch
            .tokenFetchMaxRetries(1)
            .build();
    currentWiremockRuntimeInfo
        .getWireMock()
        .register(
            WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
                .willReturn(WireMock.aResponse().withFixedDelay(10_000)));

    // when/then
    assertThatCode(() -> provider.applyCredentials(applier))
        .isInstanceOf(SocketTimeoutException.class);
    currentWiremockRuntimeInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @Test
  void shouldCallOauthServerOnlyOnceInMultithreadMode() {
    // given
    final OAuthCredentialsProvider provider =
        spy(
            new OAuthCredentialsProviderBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(SECRET)
                .audience(AUDIENCE)
                .authorizationServerUrl(tokenUrlString())
                .credentialsCachePath(cacheFilePath.toString())
                .build());
    mockCredentials(ACCESS_TOKEN, null);

    // when
    CompletableFuture.allOf(
            IntStream.range(0, 10)
                .mapToObj(
                    ignored ->
                        CompletableFuture.runAsync(
                            () -> {
                              try {
                                provider.applyCredentials(applier);
                              } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                              }
                            }))
                .toArray(CompletableFuture[]::new))
        .join();

    // then
    assertThat(applier.getCredentials())
        .containsOnly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN))
        .hasSize(10);
    currentWiremockRuntimeInfo.getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
  }

  @Test
  void shouldRequestTokenWithResourceAndAddToCall() throws IOException {
    // given
    final String resource = "https://api.example.com";
    final OAuthCredentialsProvider provider =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .audience(AUDIENCE)
            .resource(resource)
            .authorizationServerUrl(tokenUrlString())
            .credentialsCachePath(cacheFilePath.toString())
            .build();
    mockCredentialsWithResource(ACCESS_TOKEN, null, resource);

    // when
    provider.applyCredentials(applier);

    // then
    assertThat(applier.getCredentials())
        .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
  }

  private void mockCredentials(final String token, final String scope) {
    mockCredentialsWithResource(token, scope, null);
  }

  private void mockCredentialsWithResource(
      final String token, final String scope, final String resource) {
    final HashMap<String, String> map = new HashMap<>();
    map.put("client_secret", SECRET);
    map.put("client_id", CLIENT_ID);
    map.put("audience", AUDIENCE);
    map.put("grant_type", "client_credentials");
    if (scope != null) {
      map.put("scope", scope);
    }
    if (resource != null) {
      map.put("resource", resource);
    }

    final String encodedBody =
        map.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(Collectors.joining("&"));

    // Create response body - resource parameter should not be included in response
    final HashMap<String, String> responseMap = new HashMap<>();
    responseMap.put("access_token", token);
    responseMap.put("token_type", TOKEN_TYPE);
    responseMap.put(
        "expires_in",
        String.valueOf(
            EXPIRY.getLong(ChronoField.INSTANT_SECONDS) - Instant.now().getEpochSecond()));
    if (scope != null) {
      responseMap.put("scope", scope);
    }

    try {
      final String body = jsonMapper.writeValueAsString(responseMap);
      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
                  .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                  .withHeader("Accept", equalTo("application/json"))
                  .withHeader("User-Agent", matching("camunda-client-java/\\d+\\.\\d+\\.\\d+.*"))
                  .withRequestBody(equalTo(encodedBody))
                  .willReturn(
                      WireMock.aResponse().withBody(body).withFixedDelay(0).withStatus(200)));
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private void mockTokenRequest(final boolean withAssertion) {
    mockTokenRequestWithResource(withAssertion, null);
  }

  private void mockTokenRequestWithResource(final boolean withAssertion, final String resource) {
    final String assertionRegex = ".*client_assertion\\=[\\._\\-A-Za-z0-9]{400,500}.*";
    final String assertionTypeRegex = ".*client_assertion_type.*";
    final String clientSecret = ".*client_secret.*";
    final String resourceRegex = resource != null ? ".*resource.*" : ".*";
    final HashMap<String, String> map = new HashMap<>();
    map.put("access_token", ACCESS_TOKEN);
    map.put("token_type", TOKEN_TYPE);
    map.put("expires_in", "3600");

    try {
      com.github.tomakehurst.wiremock.client.MappingBuilder requestBuilder =
          WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
              .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
              .withHeader("Accept", equalTo("application/json"))
              .withHeader("User-Agent", matching("camunda-client-java/\\d+\\.\\d+\\.\\d+.*"))
              .withRequestBody(
                  withAssertion ? matching(assertionRegex) : notMatching(assertionRegex))
              .withRequestBody(
                  withAssertion ? matching(assertionTypeRegex) : notMatching(assertionTypeRegex))
              .withRequestBody(!withAssertion ? matching(clientSecret) : notMatching(clientSecret));

      if (resource != null) {
        requestBuilder = requestBuilder.withRequestBody(matching(resourceRegex));
      }

      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              requestBuilder.willReturn(
                  WireMock.aResponse()
                      .withBody(jsonMapper.writeValueAsString(map))
                      .withHeader("Content-Type", "application/json")
                      .withStatus(200)));
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static String encode(final String param) {
    try {
      return URLEncoder.encode(param, StandardCharsets.UTF_8.name());
    } catch (final UnsupportedEncodingException e) {
      throw new UncheckedIOException("Failed while encoding OAuth request parameters: ", e);
    }
  }

  private String tokenUrlString() {
    return currentWiremockRuntimeInfo.getHttpBaseUrl() + "/oauth/token";
  }

  private String tokenHttpsUrlString() {
    return currentWiremockRuntimeInfo.getHttpsBaseUrl() + "/oauth/token";
  }

  private static final class TestStatusCode implements StatusCode {
    private final int code;
    private final boolean isUnauthorized;

    private TestStatusCode(final int code, final boolean isUnauthorized) {
      this.code = code;
      this.isUnauthorized = isUnauthorized;
    }

    @Override
    public int code() {
      return code;
    }

    @Override
    public boolean isUnauthorized() {
      return isUnauthorized;
    }
  }

  @Nested
  final class ClientTest {

    private final RecordingGatewayService grpcService = new RecordingGatewayService();
    private final RecordingInterceptor recordingInterceptor = new RecordingInterceptor();
    private final Server grpcServer =
        NettyServerBuilder.forPort(0)
            .addService(ServerInterceptors.intercept(grpcService, recordingInterceptor))
            .build();

    @BeforeEach
    void beforeEach() throws IOException {
      grpcServer.start();
    }

    @AfterEach
    void afterEach() {
      grpcServer.shutdownNow();
      recordingInterceptor.reset();
    }

    @Test
    void shouldRetryRequestWithNewCredentialsGrpc() throws URISyntaxException, IOException {
      // given
      final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFilePath.toFile());
      final CamundaClientBuilder builder = clientBuilder();
      cache
          .put(CLIENT_ID, new CamundaClientCredentials("firstToken", EXPIRY, TOKEN_TYPE))
          .writeCache();
      recordingInterceptor.setInterceptAction(
          (call, headers) -> {
            mockCredentials(ACCESS_TOKEN, null);
            recordingInterceptor.reset();
            call.close(
                Status.fromCode(Code.UNAUTHENTICATED).augmentDescription("Stale token"), headers);
          });

      // when
      final Future<Topology> topology;
      try (final CamundaClient client = builder.build()) {
        topology = client.newTopologyRequest().useGrpc().send();

        // then
        assertThat(topology).succeedsWithin(Duration.ofSeconds(5));
        WireMock.verify(1, RequestPatternBuilder.newRequestPattern().withUrl("/oauth/token"));
        assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
            .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
      }
    }

    @Test
    void shouldNotRetryRequestWithSameCredentialsGrpc() throws URISyntaxException, IOException {
      // given
      final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFilePath.toFile());
      final CamundaClientBuilder builder = clientBuilder();
      cache
          .put(CLIENT_ID, new CamundaClientCredentials(ACCESS_TOKEN, EXPIRY, TOKEN_TYPE))
          .writeCache();
      recordingInterceptor.setInterceptAction(
          (call, headers) -> call.close(Status.UNAUTHENTICATED, headers));
      mockCredentials(ACCESS_TOKEN, null);

      // when
      final Future<Topology> topology;
      try (final CamundaClient client = builder.build()) {
        topology = client.newTopologyRequest().useGrpc().send();

        // then
        assertThat(topology)
            .failsWithin(Duration.ofSeconds(5))
            .withThrowableThat()
            .withRootCauseInstanceOf(StatusRuntimeException.class);
        WireMock.verify(1, RequestPatternBuilder.newRequestPattern().withUrl("/oauth/token"));
        assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
            .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
      }
    }

    @Test
    void shouldRetryRequestWithNewCredentialsRest() throws URISyntaxException, IOException {
      // given
      final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFilePath.toFile());
      final CamundaClientBuilder builder = clientBuilder();
      mockUnauthorizedRestRequest();
      mockAuthorizedRestRequest();
      cache
          .put(CLIENT_ID, new CamundaClientCredentials("firstToken", EXPIRY, TOKEN_TYPE))
          .writeCache();
      mockCredentials(ACCESS_TOKEN, null);

      // when
      final Future<Topology> topology;
      try (final CamundaClient client = builder.build()) {
        topology = client.newTopologyRequest().useRest().send();

        // then
        assertThat(topology).succeedsWithin(Duration.ofSeconds(5));
        WireMock.verify(1, RequestPatternBuilder.newRequestPattern().withUrl("/oauth/token"));
      }
    }

    @Test
    void shouldNotRetryRequestWithSameCredentialsRest() throws URISyntaxException, IOException {
      // given
      final OAuthCredentialsCache cache = new OAuthCredentialsCache(cacheFilePath.toFile());
      final CamundaClientBuilder builder = clientBuilder();
      mockUnauthorizedRestRequest();
      cache
          .put(CLIENT_ID, new CamundaClientCredentials(ACCESS_TOKEN, EXPIRY, TOKEN_TYPE))
          .writeCache();
      mockCredentials(ACCESS_TOKEN, null);

      // when
      final Future<Topology> topology;
      try (final CamundaClient client = builder.build()) {
        topology = client.newTopologyRequest().useRest().send();

        // then
        assertThat(topology).failsWithin(Duration.ofSeconds(5));
        WireMock.verify(1, RequestPatternBuilder.newRequestPattern().withUrl("/oauth/token"));
      }
    }

    private void mockAuthorizedRestRequest() throws JsonProcessingException {
      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              WireMock.get(RestGatewayPaths.getTopologyUrl())
                  .withHeader("Authorization", WireMock.equalTo(TOKEN_TYPE + " " + ACCESS_TOKEN))
                  .willReturn(
                      WireMock.aResponse()
                          .withStatus(200)
                          .withBody(
                              JSON_MAPPER.writeValueAsBytes(
                                  new TopologyResponse()
                                      .brokers(new ArrayList<>())
                                      .clusterSize(0)
                                      .partitionsCount(0)
                                      .replicationFactor(0)
                                      .gatewayVersion("")))
                          .withHeader(
                              HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")));
    }

    private void mockUnauthorizedRestRequest() throws JsonProcessingException {
      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              WireMock.get(RestGatewayPaths.getTopologyUrl())
                  .willReturn(
                      WireMock.unauthorized()
                          .withBody(JSON_MAPPER.writeValueAsBytes(new ProblemDetail()))
                          .withHeader(
                              HttpHeaders.CONTENT_TYPE,
                              "application/problem+json; charset=utf-8")));
    }

    private CamundaClientBuilder clientBuilder() throws URISyntaxException {
      return CamundaClient.newClientBuilder()
          .grpcAddress(new URI("http://localhost:" + grpcServer.getPort()))
          .restAddress(new URI(currentWiremockRuntimeInfo.getHttpBaseUrl()))
          .credentialsProvider(
              new OAuthCredentialsProviderBuilder()
                  .clientId(CLIENT_ID)
                  .clientSecret(SECRET)
                  .audience(AUDIENCE)
                  .authorizationServerUrl(tokenUrlString())
                  .credentialsCachePath(cacheFilePath.toString())
                  .build());
    }
  }

  @Nested
  final class EntraTests {

    @Test
    void shouldUseEntraCredentialsWhenProvidedBoth() throws IOException {
      currentWiremockRuntimeInfo = httpsWiremock.getRuntimeInfo();

      final OAuthCredentialsProvider provider =
          initializeCredentialsProviderBuilder(true, true).build();
      mockTokenRequest(true);

      provider.applyCredentials(applier);

      final List<Credential> credentials = applier.getCredentials();
      assertThat(credentials)
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
    }

    @Test
    void shouldUseEntraCredentialsWhenProvidedOnlyAssertion() throws IOException {
      currentWiremockRuntimeInfo = httpsWiremock.getRuntimeInfo();

      final OAuthCredentialsProvider provider =
          initializeCredentialsProviderBuilder(true, false).build();
      mockTokenRequest(true);

      provider.applyCredentials(applier);

      final List<Credential> credentials = applier.getCredentials();
      assertThat(credentials)
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
    }

    @Test
    void shouldUseClientSecretWhenNoAssertionProvided() throws IOException {
      currentWiremockRuntimeInfo = httpsWiremock.getRuntimeInfo();

      final OAuthCredentialsProvider provider =
          initializeCredentialsProviderBuilder(false, true).build();
      mockTokenRequest(false);

      provider.applyCredentials(applier);

      final List<Credential> credentials = applier.getCredentials();
      assertThat(credentials)
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
    }

    @Test
    void shouldUseEntraCredentialsWithResourceParameter() throws IOException {
      currentWiremockRuntimeInfo = httpsWiremock.getRuntimeInfo();
      final String resource = "https://api.example.com";

      final OAuthCredentialsProvider provider =
          initializeCredentialsProviderBuilder(true, true).resource(resource).build();
      mockTokenRequestWithResource(true, resource);

      provider.applyCredentials(applier);

      final List<Credential> credentials = applier.getCredentials();
      assertThat(credentials)
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
    }

    private OAuthCredentialsProviderBuilder initializeCredentialsProviderBuilder(
        final boolean withAssertion, final boolean withClientSecret) {
      OAuthCredentialsProviderBuilder builder =
          new OAuthCredentialsProviderBuilder()
              .clientId(CLIENT_ID)
              .keystorePath(Paths.get(VALID_CLIENT_PATH))
              .keystorePassword(KEYSTORE_PASSWORD)
              .keystoreKeyPassword(KEYSTORE_MATERIAL_PASSWORD)
              .truststorePath(Paths.get(VALID_TRUSTSTORE_PATH))
              .truststorePassword(TRUSTSTORE_PASSWORD)
              .audience(AUDIENCE)
              .authorizationServerUrl(tokenHttpsUrlString())
              .credentialsCachePath(cacheFilePath.toString());
      if (withAssertion) {
        builder =
            builder
                .clientAssertionKeystorePath(OAUTH_SSL_CLIENT_CERT_PATH)
                .clientAssertionKeystorePassword(OAUTH_SSL_CLIENT_CERT_PASSWORD);
      }
      if (withClientSecret) {
        builder = builder.clientSecret(SECRET);
      }
      return builder;
    }
  }

  @Nested
  final class CustomSSLTests {

    @Test
    void shouldSucceedSSLConnection() throws IOException {
      currentWiremockRuntimeInfo = httpsWiremock.getRuntimeInfo();
      // given
      final OAuthCredentialsProvider provider = initializeBuilderWithSSL().build();
      mockCredentials(ACCESS_TOKEN, null);

      // when
      provider.applyCredentials(applier);

      // then
      assertThat(applier.getCredentials())
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
    }

    @Test
    void shouldFailWhenSSLCredsNotSpecified() throws IOException {
      currentWiremockRuntimeInfo = httpsWiremock.getRuntimeInfo();
      // given
      final OAuthCredentialsProvider provider =
          initializeBuilderWithSSL()
              .keystorePath(null)
              .keystorePassword(null)
              .keystoreKeyPassword(null)
              .truststorePath(null)
              .truststorePassword(null)
              .build();
      mockCredentials(ACCESS_TOKEN, null);

      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .hasMessageContaining("unable to find valid certification path to requested target");
    }

    @Test
    void shouldFailWhenTrustStoreDoesNotContainCorrectRootCACert() throws IOException {
      currentWiremockRuntimeInfo = httpsWiremock.getRuntimeInfo();
      // given
      final OAuthCredentialsProvider provider =
          initializeBuilderWithSSL().truststorePath(Paths.get(TAMPERED_TRUSTSTORE_PATH)).build();
      mockCredentials(ACCESS_TOKEN, null);

      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(SSLHandshakeException.class)
          .hasMessageContaining("signature check failed");
    }

    @Test
    void shouldFailWhenKeystorePasswordIsIncorrect() throws IOException {
      currentWiremockRuntimeInfo = httpsWiremock.getRuntimeInfo();
      // given
      final OAuthCredentialsProvider provider =
          initializeBuilderWithSSL().keystorePassword("qwerty").build();

      mockCredentials(ACCESS_TOKEN, null);

      // when
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(RuntimeException.class)
          .hasStackTraceContaining("keystore password was incorrect");
    }

    @Test
    void shouldFailWhenMaterialPasswordIsIncorrect() throws IOException {
      currentWiremockRuntimeInfo = httpsWiremock.getRuntimeInfo();
      // given
      final OAuthCredentialsProvider provider =
          initializeBuilderWithSSL().keystoreKeyPassword("qwerty").build();

      mockCredentials(ACCESS_TOKEN, null);

      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(RuntimeException.class)
          .hasStackTraceContaining("bad key is used during decryption");
    }

    private OAuthCredentialsProviderBuilder initializeBuilderWithSSL() {
      return new OAuthCredentialsProviderBuilder()
          .clientId(CLIENT_ID)
          .clientSecret(SECRET)
          .keystorePath(Paths.get(VALID_CLIENT_PATH))
          .keystorePassword(KEYSTORE_PASSWORD)
          .keystoreKeyPassword(KEYSTORE_MATERIAL_PASSWORD)
          .truststorePath(Paths.get(VALID_TRUSTSTORE_PATH))
          .truststorePassword(TRUSTSTORE_PASSWORD)
          .audience(AUDIENCE)
          .authorizationServerUrl(tokenHttpsUrlString())
          .credentialsCachePath(cacheFilePath.toString());
    }
  }

  @Nested
  final class RetryAndLatchTests {

    private static final String SCENARIO = "token-retry";

    private OAuthCredentialsProviderBuilder retryProviderBuilder() {
      return new OAuthCredentialsProviderBuilder()
          .clientId(CLIENT_ID)
          .clientSecret(SECRET)
          .audience(AUDIENCE)
          .authorizationServerUrl(tokenUrlString())
          .credentialsCachePath(cacheFilePath.toString())
          // keep wall-clock cost negligible
          .tokenFetchInitialBackoff(Duration.ofMillis(10))
          .tokenFetchBackoffMultiplier(2.0)
          .tokenFetchMaxRetries(5);
    }

    private void stubTokenStatus(final int status, final String fromState, final String toState) {
      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
                  .inScenario(SCENARIO)
                  .whenScenarioStateIs(fromState)
                  .willReturn(WireMock.aResponse().withStatus(status))
                  .willSetStateTo(toState));
    }

    private void stubTokenSuccess(final String fromState) {
      final HashMap<String, String> body = new HashMap<>();
      body.put("access_token", ACCESS_TOKEN);
      body.put("token_type", TOKEN_TYPE);
      body.put(
          "expires_in",
          String.valueOf(
              EXPIRY.getLong(ChronoField.INSTANT_SECONDS) - Instant.now().getEpochSecond()));
      try {
        currentWiremockRuntimeInfo
            .getWireMock()
            .register(
                WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
                    .inScenario(SCENARIO)
                    .whenScenarioStateIs(fromState)
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withBody(jsonMapper.writeValueAsString(body))));
      } catch (final JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    private void stubAlwaysStatus(final int status) {
      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
                  .willReturn(WireMock.aResponse().withStatus(status)));
    }

    private int requestCount() {
      return currentWiremockRuntimeInfo
          .getWireMock()
          .find(RequestPatternBuilder.allRequests())
          .size();
    }

    @Test
    void shouldRetryOn429WithExponentialBackoff() throws IOException {
      // given
      stubTokenStatus(429, com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED, "second");
      stubTokenStatus(429, "second", "third");
      stubTokenSuccess("third");
      final OAuthCredentialsProvider provider = retryProviderBuilder().build();

      // when
      provider.applyCredentials(applier);

      // then
      assertThat(applier.getCredentials())
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
      assertThat(requestCount()).isEqualTo(3);
    }

    @Test
    void shouldRetryOn5xx() throws IOException {
      // given
      stubTokenStatus(503, com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED, "second");
      stubTokenSuccess("second");
      final OAuthCredentialsProvider provider = retryProviderBuilder().build();

      // when
      provider.applyCredentials(applier);

      // then
      assertThat(applier.getCredentials())
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
      assertThat(requestCount()).isEqualTo(2);
    }

    @Test
    void shouldRetryOn404() throws IOException {
      // given
      stubTokenStatus(404, com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED, "second");
      stubTokenSuccess("second");
      final OAuthCredentialsProvider provider = retryProviderBuilder().build();

      // when
      provider.applyCredentials(applier);

      // then
      assertThat(applier.getCredentials())
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
      assertThat(requestCount()).isEqualTo(2);
    }

    @Test
    void shouldFailAfterMaxRetriesWithoutLatching() throws IOException {
      // given
      stubAlwaysStatus(429);
      final OAuthCredentialsProvider provider =
          retryProviderBuilder().tokenFetchMaxRetries(2).build();

      // when / then — first call fails after 2 attempts, surfacing the last 429
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("status code 429");
      assertThat(requestCount()).isEqualTo(2);

      // and a second call retries again (no latch)
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("status code 429");
      assertThat(requestCount()).isEqualTo(4);
    }

    @Test
    void shouldTripLatchOn401AndFailAllSubsequentCallsWithoutHttpRequest() {
      // given
      stubAlwaysStatus(401);
      final OAuthCredentialsProvider provider = retryProviderBuilder().build();

      // when — first call hits the endpoint and trips the latch
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("status code 401");
      final int afterFirst = requestCount();
      assertThat(afterFirst).isEqualTo(1);

      // then — second call must NOT hit the endpoint
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("non-retryable failure cooldown");
      assertThat(requestCount()).isEqualTo(afterFirst);
    }

    @Test
    void shouldTripLatchOn403() {
      // given
      stubAlwaysStatus(403);
      final OAuthCredentialsProvider provider = retryProviderBuilder().build();

      // when
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("status code 403");
      assertThat(requestCount()).isEqualTo(1);

      // then
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("non-retryable failure cooldown");
      assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void shouldTripLatchOn400() {
      // given
      stubAlwaysStatus(400);
      final OAuthCredentialsProvider provider = retryProviderBuilder().build();

      // when
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("status code 400");
      assertThat(requestCount()).isEqualTo(1);

      // then
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("non-retryable failure cooldown");
      assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void shouldRespectConfiguredMaxRetries() {
      // given
      stubAlwaysStatus(429);
      final OAuthCredentialsProvider provider =
          retryProviderBuilder().tokenFetchMaxRetries(1).build();

      // when
      assertThatThrownBy(() -> provider.applyCredentials(applier)).isInstanceOf(IOException.class);

      // then — exactly 1 attempt, no retries
      assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void shouldHonorCustomRetryableStatusCodes() throws IOException {
      // given — make 418 retryable, override the default set entirely
      stubTokenStatus(418, com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED, "second");
      stubTokenSuccess("second");
      final OAuthCredentialsProvider provider =
          retryProviderBuilder()
              .tokenFetchRetryableStatusCodes(new java.util.HashSet<>(java.util.Arrays.asList(418)))
              .build();

      // when
      provider.applyCredentials(applier);

      // then
      assertThat(applier.getCredentials())
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
      assertThat(requestCount()).isEqualTo(2);
    }

    @Test
    void shouldLatchOnStatusCodeRemovedFromRetryableSet() {
      // given — drop 429 from the default retryable set
      stubAlwaysStatus(429);
      final java.util.Set<Integer> withoutFourTwentyNine =
          new java.util.HashSet<>(
              io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder
                  .DEFAULT_TOKEN_FETCH_RETRYABLE_STATUS_CODES);
      withoutFourTwentyNine.remove(429);
      final OAuthCredentialsProvider provider =
          retryProviderBuilder().tokenFetchRetryableStatusCodes(withoutFourTwentyNine).build();

      // when — first call hits the endpoint and trips the latch
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("status code 429");
      assertThat(requestCount()).isEqualTo(1);

      // then — second call must NOT hit the endpoint
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("non-retryable failure cooldown");
      assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void builderShouldRejectInvalidConfig() {
      assertThatThrownBy(() -> retryProviderBuilder().tokenFetchMaxRetries(0).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("tokenFetchMaxRetries");
      assertThatThrownBy(
              () -> retryProviderBuilder().tokenFetchInitialBackoff(Duration.ZERO).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("tokenFetchInitialBackoff");
      assertThatThrownBy(() -> retryProviderBuilder().tokenFetchBackoffMultiplier(0.5).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("tokenFetchBackoffMultiplier");
    }

    @Test
    void shouldHonorRetryAfterHeaderOn429() throws IOException {
      // given — first response is a 429 with Retry-After: 1 (1 second), second is success.
      // Use a very small initial backoff so we can distinguish the Retry-After delay (~1000ms)
      // from the computed backoff (~10ms).
      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
                  .inScenario(SCENARIO)
                  .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                  .willReturn(WireMock.aResponse().withStatus(429).withHeader("Retry-After", "1"))
                  .willSetStateTo("second"));
      stubTokenSuccess("second");
      final OAuthCredentialsProvider provider = retryProviderBuilder().build();

      // when
      final long started = System.nanoTime();
      provider.applyCredentials(applier);
      final long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

      // then — success, and we waited at least ~1s honoring the header
      assertThat(applier.getCredentials())
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
      assertThat(requestCount()).isEqualTo(2);
      assertThat(elapsedMs).isGreaterThanOrEqualTo(900L);
    }

    @Test
    void shouldProbeAfterNonRetryableCooldownElapses() {
      // given — 401 on first call, success on second. Cooldown is 50ms so the test waits briefly.
      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
                  .inScenario(SCENARIO)
                  .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                  .willReturn(WireMock.aResponse().withStatus(401))
                  .willSetStateTo("after-401"));
      stubTokenSuccess("after-401");
      final OAuthCredentialsProvider provider =
          retryProviderBuilder().tokenFetchNonRetryableCooldown(Duration.ofMillis(50)).build();

      // when — first call trips the latch
      assertThatThrownBy(() -> provider.applyCredentials(applier)).isInstanceOf(IOException.class);
      assertThat(requestCount()).isEqualTo(1);

      // during cooldown — still fail fast
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("non-retryable failure cooldown");
      assertThat(requestCount()).isEqualTo(1);

      // after cooldown — fresh attempt succeeds
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(() -> provider.applyCredentials(applier));
      assertThat(requestCount()).isEqualTo(2);
      assertThat(applier.getCredentials())
          .contains(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
    }

    @Test
    void shouldReArmLatchOnRepeatedNonRetryableFailure() {
      // given — always 401, short cooldown
      stubAlwaysStatus(401);
      final OAuthCredentialsProvider provider =
          retryProviderBuilder().tokenFetchNonRetryableCooldown(Duration.ofMillis(50)).build();

      // when — first trip
      assertThatThrownBy(() -> provider.applyCredentials(applier)).isInstanceOf(IOException.class);
      assertThat(requestCount()).isEqualTo(1);

      // wait for cooldown to elapse, probe re-fails and re-arms
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(
              () ->
                  assertThatThrownBy(() -> provider.applyCredentials(applier))
                      .isInstanceOf(IOException.class)
                      .hasMessageNotContaining("non-retryable failure cooldown"));
      assertThat(requestCount()).isEqualTo(2);

      // immediately after re-arm — fail fast again
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("non-retryable failure cooldown");
      assertThat(requestCount()).isEqualTo(2);
    }

    @Test
    void shouldRetryRequestShouldShortCircuitWhenLatchTripped() {
      // given — trip the latch via a 401
      stubAlwaysStatus(401);
      final OAuthCredentialsProvider provider = retryProviderBuilder().build();

      assertThatThrownBy(() -> provider.applyCredentials(applier)).isInstanceOf(IOException.class);
      assertThat(requestCount()).isEqualTo(1);

      // when — gRPC interceptor calls shouldRetryRequest with a 401 status
      final boolean shouldRetry =
          provider.shouldRetryRequest(
              new io.camunda.client.CredentialsProvider.StatusCode() {
                @Override
                public int code() {
                  return 401;
                }

                @Override
                public boolean isUnauthorized() {
                  return true;
                }
              });

      // then — no retry, no extra HTTP request to the token endpoint
      assertThat(shouldRetry).isFalse();
      assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void shouldRetryOnIOExceptionAndEventuallySucceed() throws IOException {
      // given — first request resets the connection (IOException on client side), second succeeds
      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
                  .inScenario(SCENARIO)
                  .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                  .willReturn(
                      WireMock.aResponse()
                          .withFault(
                              com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER))
                  .willSetStateTo("after-reset"));
      stubTokenSuccess("after-reset");
      final OAuthCredentialsProvider provider = retryProviderBuilder().build();

      // when
      provider.applyCredentials(applier);

      // then — network error was retried and the retry succeeded
      assertThat(applier.getCredentials())
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
      assertThat(requestCount()).isEqualTo(2);
    }

    @Test
    void builderShouldRejectSubMillisecondInitialBackoff() {
      assertThatThrownBy(
              () -> retryProviderBuilder().tokenFetchInitialBackoff(Duration.ofNanos(500)).build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("tokenFetchInitialBackoff")
          .hasMessageContaining("at least 1 millisecond");
    }

    @Test
    void shouldDefensivelyCopyRetryableStatusCodesOnBuild() throws IOException {
      // given — build with a mutable set containing {404, 429, 500, 502, 503, 504}
      final java.util.Set<Integer> mutableSet =
          new java.util.HashSet<>(
              io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder
                  .DEFAULT_TOKEN_FETCH_RETRYABLE_STATUS_CODES);
      final OAuthCredentialsProvider provider =
          retryProviderBuilder().tokenFetchRetryableStatusCodes(mutableSet).build();

      // when — caller mutates the original set after building, adding 418
      mutableSet.add(418);

      // then — provider still treats 418 as non-retryable (latches immediately on it)
      stubAlwaysStatus(418);
      assertThatThrownBy(() -> provider.applyCredentials(applier))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("status code 418");
      assertThat(requestCount()).isEqualTo(1);
    }

    @Test
    void shouldHonorRetryAfterHeaderAsHttpDate() throws IOException {
      // given — Retry-After header uses RFC 1123 HTTP-date format, ~2s in the future.
      // HTTP-date has second-level resolution, so we need > 1s of cushion to reliably
      // assert the delay was honored without sub-second rounding flakiness.
      final String httpDate =
          java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
              java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusSeconds(2));
      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
                  .inScenario(SCENARIO)
                  .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                  .willReturn(
                      WireMock.aResponse().withStatus(503).withHeader("Retry-After", httpDate))
                  .willSetStateTo("second"));
      stubTokenSuccess("second");
      final OAuthCredentialsProvider provider = retryProviderBuilder().build();

      // when
      final long started = System.nanoTime();
      provider.applyCredentials(applier);
      final long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

      // then — honored the header, waited at least ~1s (well beyond the 10ms computed backoff)
      assertThat(applier.getCredentials())
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
      assertThat(requestCount()).isEqualTo(2);
      assertThat(elapsedMs).isGreaterThanOrEqualTo(1000L);
    }

    @Test
    void shouldHonorRetryAfterHeaderEvenWhenShorterThanComputedBackoff() throws IOException {
      // given — a large computed backoff (5s) but a short Retry-After header (0s).
      // Retry-After must win unconditionally, even when shorter than the computed backoff.
      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
                  .inScenario(SCENARIO)
                  .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                  .willReturn(WireMock.aResponse().withStatus(429).withHeader("Retry-After", "0"))
                  .willSetStateTo("second"));
      stubTokenSuccess("second");
      final OAuthCredentialsProvider provider =
          retryProviderBuilder()
              .tokenFetchInitialBackoff(Duration.ofSeconds(5))
              .tokenFetchMaxRetries(2)
              .build();

      // when
      final long started = System.nanoTime();
      provider.applyCredentials(applier);
      final long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

      // then — succeeded, and elapsed time is well below the computed backoff of 5s,
      // proving Retry-After: 0 was honored rather than the larger computed delay.
      assertThat(applier.getCredentials())
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
      assertThat(requestCount()).isEqualTo(2);
      assertThat(elapsedMs).isLessThan(3_000L);
    }

    @Test
    void shouldFallBackToComputedBackoffWhenRetryAfterUnparseable() throws IOException {
      // given — unparseable Retry-After value should be ignored, falling back to the
      // computed (jittered) backoff based on initial=10ms
      currentWiremockRuntimeInfo
          .getWireMock()
          .register(
              WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
                  .inScenario(SCENARIO)
                  .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                  .willReturn(
                      WireMock.aResponse().withStatus(429).withHeader("Retry-After", "garbage"))
                  .willSetStateTo("second"));
      stubTokenSuccess("second");
      final OAuthCredentialsProvider provider = retryProviderBuilder().build();

      // when
      final long started = System.nanoTime();
      provider.applyCredentials(applier);
      final long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

      // then — succeeded, and elapsed time stayed near the computed backoff (well under 1s),
      // proving the unparseable header did not cause us to wait the default Retry-After of 1s
      assertThat(applier.getCredentials())
          .containsExactly(new Credential("Authorization", TOKEN_TYPE + " " + ACCESS_TOKEN));
      assertThat(requestCount()).isEqualTo(2);
      assertThat(elapsedMs).isLessThan(500L);
    }

    @Test
    void builderShouldRejectNegativeCooldown() {
      assertThatThrownBy(
              () ->
                  retryProviderBuilder()
                      .tokenFetchNonRetryableCooldown(Duration.ofSeconds(-1))
                      .build())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("tokenFetchNonRetryableCooldown")
          .hasMessageContaining("non-negative");
    }

    @Test
    void shouldBuildProviderFromAllTokenFetchEnvVars() throws IOException {
      // given — set every new token-fetch env var, with values distinct from the defaults
      final io.camunda.client.impl.util.Environment env =
          io.camunda.client.impl.util.Environment.system();
      env.put("CAMUNDA_AUTH_TOKEN_FETCH_MAX_RETRIES", "2");
      env.put("CAMUNDA_AUTH_TOKEN_FETCH_INITIAL_BACKOFF_MS", "10");
      env.put("CAMUNDA_AUTH_TOKEN_FETCH_BACKOFF_MULTIPLIER", "3.0");
      env.put("CAMUNDA_AUTH_TOKEN_FETCH_RETRYABLE_STATUS_CODES", "418, 429");
      env.put("CAMUNDA_AUTH_TOKEN_FETCH_NON_RETRYABLE_COOLDOWN_MS", "12345");
      try {
        // when — build without explicit overrides: env vars must flow through
        final OAuthCredentialsProviderBuilder builder =
            new OAuthCredentialsProviderBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(SECRET)
                .audience(AUDIENCE)
                .authorizationServerUrl(tokenUrlString())
                .credentialsCachePath(cacheFilePath.toString());
        builder.build();

        // then — every knob reflects the env var value
        assertThat(builder.getTokenFetchMaxRetries()).isEqualTo(2);
        assertThat(builder.getTokenFetchInitialBackoff()).isEqualTo(Duration.ofMillis(10));
        assertThat(builder.getTokenFetchBackoffMultiplier()).isEqualTo(3.0);
        assertThat(builder.getTokenFetchRetryableStatusCodes()).containsExactlyInAnyOrder(418, 429);
        assertThat(builder.getTokenFetchNonRetryableCooldown()).isEqualTo(Duration.ofMillis(12345));
      } finally {
        env.remove("CAMUNDA_AUTH_TOKEN_FETCH_MAX_RETRIES");
        env.remove("CAMUNDA_AUTH_TOKEN_FETCH_INITIAL_BACKOFF_MS");
        env.remove("CAMUNDA_AUTH_TOKEN_FETCH_BACKOFF_MULTIPLIER");
        env.remove("CAMUNDA_AUTH_TOKEN_FETCH_RETRYABLE_STATUS_CODES");
        env.remove("CAMUNDA_AUTH_TOKEN_FETCH_NON_RETRYABLE_COOLDOWN_MS");
      }
    }
  }
}
