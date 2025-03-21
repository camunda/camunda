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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.net.ssl.SSLHandshakeException;
import org.apache.hc.core5.http.HttpHeaders;
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

  private static final String KEYSTORE_MATERIAL_PASSWORD = "password";
  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final Key<String> AUTH_KEY =
      Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  private static final ZonedDateTime EXPIRY =
      ZonedDateTime.of(3020, 1, 1, 0, 0, 0, 0, ZoneId.of("Z"));
  private static final String SECRET = "secret";
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

  private void mockCredentials(final String token, final String scope) {
    final HashMap<String, String> map = new HashMap<>();
    map.put("client_secret", SECRET);
    map.put("client_id", CLIENT_ID);
    map.put("audience", AUDIENCE);
    map.put("grant_type", "client_credentials");
    if (scope != null) {
      map.put("scope", scope);
    }

    final String encodedBody =
        map.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(Collectors.joining("&"));

    map.put("access_token", token);
    map.put("token_type", TOKEN_TYPE);
    map.put(
        "expires_in",
        String.valueOf(
            EXPIRY.getLong(ChronoField.INSTANT_SECONDS) - Instant.now().getEpochSecond()));

    try {
      final String body = jsonMapper.writeValueAsString(map);
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
      // Turn into a runtime exception so we don't have to add it to all test cases.
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
          .usePlaintext()
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
}
