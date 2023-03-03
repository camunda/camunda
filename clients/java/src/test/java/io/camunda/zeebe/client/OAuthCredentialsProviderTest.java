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
package io.camunda.zeebe.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder.OAUTH_ENV_AUTHORIZATION_SERVER;
import static io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder.OAUTH_ENV_CLIENT_ID;
import static io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder.OAUTH_ENV_CLIENT_SECRET;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.Topology;
import io.camunda.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.camunda.zeebe.client.impl.ZeebeClientCredentials;
import io.camunda.zeebe.client.impl.ZeebeClientImpl;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsCache;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.client.impl.util.Environment;
import io.camunda.zeebe.client.impl.util.EnvironmentRule;
import io.camunda.zeebe.client.util.RecordingGatewayService;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerCall;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.testing.GrpcServerRule;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@SuppressWarnings("ResultOfMethodCallIgnored")
public final class OAuthCredentialsProviderTest {

  public static final ZonedDateTime EXPIRY =
      ZonedDateTime.of(3020, 1, 1, 0, 0, 0, 0, ZoneId.of("Z"));
  private static final Key<String> AUTH_KEY =
      Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  private static final String SECRET = "secret";
  private static final String AUDIENCE = "endpoint";
  private static final String ACCESS_TOKEN = "someToken";
  private static final String TOKEN_TYPE = "Bearer";
  private static final String CLIENT_ID = "client";
  @Rule public final GrpcServerRule serverRule = new GrpcServerRule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();
  @Rule public final EnvironmentRule environmentRule = new EnvironmentRule();

  @Rule public final WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

  private final RecordingInterceptor recordingInterceptor = new RecordingInterceptor();
  private final RecordingGatewayService gatewayService = new RecordingGatewayService();
  private ZeebeClient client;
  private String cachedUserHome;

  @Before
  public void setUp() {
    serverRule
        .getServiceRegistry()
        .addService(ServerInterceptors.intercept(gatewayService, recordingInterceptor));

    // necessary when testing defaults to ensure we don't reuse the cache
    cachedUserHome = System.getProperty("user.home");
    System.setProperty("user.home", tempFolder.getRoot().getAbsolutePath());
  }

  @After
  public void tearDown() {
    if (client != null) {
      client.close();
      client = null;
    }

    recordingInterceptor.reset();
    System.setProperty("user.home", cachedUserHome);
  }

  @Test
  public void shouldRequestTokenAndAddToCall() throws IOException {
    // given
    mockCredentials(ACCESS_TOKEN);

    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder
        .usePlaintext()
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(SECRET)
                .audience(AUDIENCE)
                .authorizationServerUrl("http://localhost:" + wireMockRule.port() + "/oauth/token")
                .credentialsCachePath(tempFolder.newFile().getPath())
                .build())
        .build()
        .close();
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
        .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
  }

  @Test
  public void shouldRetryRequestWithNewCredentials() throws IOException {
    // given
    final String firstToken = "firstToken";
    mockCredentials(firstToken);
    final BiConsumer<ServerCall, Metadata> interceptAction =
        Mockito.spy(
            new BiConsumer<ServerCall, Metadata>() {
              @Override
              public void accept(final ServerCall call, final Metadata headers) {
                mockCredentials(ACCESS_TOKEN);
                recordingInterceptor.reset();
                call.close(
                    Status.fromCode(Code.UNAUTHENTICATED).augmentDescription("Stale token"),
                    headers);
              }
            });

    recordingInterceptor.setInterceptAction(interceptAction);

    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder
        .usePlaintext()
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(SECRET)
                .audience(AUDIENCE)
                .authorizationServerUrl("http://localhost:" + wireMockRule.port() + "/oauth/token")
                .credentialsCachePath(tempFolder.newFile().getPath())
                .build())
        .build()
        .close();

    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    final ArgumentCaptor<Metadata> captor = ArgumentCaptor.forClass(Metadata.class);

    verify(interceptAction, times(1)).accept(any(ServerCall.class), captor.capture());
    assertThat(captor.getValue().get(AUTH_KEY)).isEqualTo(TOKEN_TYPE + " " + firstToken);
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
        .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
  }

  @Test
  public void shouldNotRetryWithSameCredentials() throws IOException {
    // given
    mockCredentials(ACCESS_TOKEN);
    final BiConsumer<ServerCall, Metadata> interceptAction =
        Mockito.spy(
            new BiConsumer<ServerCall, Metadata>() {
              @Override
              public void accept(final ServerCall call, final Metadata headers) {
                call.close(Status.UNAUTHENTICATED, headers);
              }
            });

    recordingInterceptor.setInterceptAction(interceptAction);

    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder
        .usePlaintext()
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(SECRET)
                .audience(AUDIENCE)
                .authorizationServerUrl("http://localhost:" + wireMockRule.port() + "/oauth/token")
                .credentialsCachePath(tempFolder.newFile().getPath())
                .build())
        .build()
        .close();

    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    assertThatThrownBy(() -> client.newTopologyRequest().send().join())
        .isInstanceOf(ClientException.class);
    verify(interceptAction, times(1)).accept(any(ServerCall.class), any(Metadata.class));
  }

  @Test
  public void shouldUseClientContactPointAsDefaultAudience() {
    // given
    final String contactPointHost = "some.domain";
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    final String authorizationServerUrl =
        "http://localhost:" + wireMockRule.port() + "/oauth/token";

    Environment.system().put(OAUTH_ENV_CLIENT_ID, CLIENT_ID);
    Environment.system().put(OAUTH_ENV_CLIENT_SECRET, SECRET);
    Environment.system().put(OAUTH_ENV_AUTHORIZATION_SERVER, authorizationServerUrl);
    mockCredentials(ACCESS_TOKEN, contactPointHost);

    builder.usePlaintext().gatewayAddress(contactPointHost + ":26500").build().close();

    // when
    client = new ZeebeClientImpl(builder, serverRule.getChannel());
    client.newTopologyRequest().send().join();
  }

  @Test
  public void shouldUseCachedCredentials() throws IOException {
    // given
    mockCredentials(ACCESS_TOKEN);
    final String cachePath = tempFolder.getRoot().getPath() + File.separator + ".credsCache";
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(new File(cachePath));
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    cache.put(AUDIENCE, new ZeebeClientCredentials(ACCESS_TOKEN, EXPIRY, TOKEN_TYPE)).writeCache();
    builder
        .usePlaintext()
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(SECRET)
                .audience(AUDIENCE)
                .authorizationServerUrl("http://localhost:" + wireMockRule.port() + "/oauth/token")
                .credentialsCachePath(cachePath)
                .build())
        .build()
        .close();
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
        .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
    verify(0, oauthRequestMatcher());
  }

  @Test
  public void shouldCacheAndReuseCredentials() throws IOException {
    // given
    mockCredentials(ACCESS_TOKEN);
    final String cachePath = tempFolder.getRoot().getPath() + File.separator + ".credsCache";

    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    final OAuthCredentialsProviderBuilder credsBuilder =
        new OAuthCredentialsProviderBuilder()
            .clientId(CLIENT_ID)
            .clientSecret(SECRET)
            .audience(AUDIENCE)
            .authorizationServerUrl("http://localhost:" + wireMockRule.port() + "/oauth/token")
            .credentialsCachePath(cachePath);
    builder.usePlaintext().credentialsProvider(credsBuilder.build()).build().close();
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();
    verify(1, oauthRequestMatcher());

    builder.usePlaintext().credentialsProvider(credsBuilder.build());
    client = new ZeebeClientImpl(builder, serverRule.getChannel());
    client.newTopologyRequest().send().join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
        .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
    verify(1, oauthRequestMatcher());
    assertCacheContents(cachePath);
  }

  @Test
  public void shouldUpdateCacheIfStale() throws IOException {
    // given
    mockCredentials(ACCESS_TOKEN);
    recordingInterceptor.setInterceptAction(
        (call, metadata) -> {
          final String authHeader = metadata.get(AUTH_KEY);
          if (authHeader != null && authHeader.endsWith("staleToken")) {
            call.close(Status.UNAUTHENTICATED, metadata);
          }
        });
    final String cachePath = tempFolder.getRoot().getPath() + File.separator + ".credsCache";
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(new File(cachePath));
    cache.put(AUDIENCE, new ZeebeClientCredentials("staleToken", EXPIRY, TOKEN_TYPE)).writeCache();

    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder
        .usePlaintext()
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(SECRET)
                .audience(AUDIENCE)
                .authorizationServerUrl("http://localhost:" + wireMockRule.port() + "/oauth/token")
                .credentialsCachePath(cachePath)
                .build())
        .build()
        .close();
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
        .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
    verify(1, oauthRequestMatcher());
    assertCacheContents(cachePath);
  }

  @Test
  public void shouldFailWithNoAudience() {
    // when/then
    assertThatThrownBy(
            () ->
                new OAuthCredentialsProviderBuilder()
                    .clientId("a")
                    .clientSecret("b")
                    .authorizationServerUrl("http://some.url")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(
            String.format(OAuthCredentialsProviderBuilder.INVALID_ARGUMENT_MSG, "audience"));
  }

  @Test
  public void shouldFailWithNoClientId() {
    // when/then
    assertThatThrownBy(
            () ->
                new OAuthCredentialsProviderBuilder()
                    .audience("a")
                    .clientSecret("b")
                    .authorizationServerUrl("http://some.url")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(
            String.format(OAuthCredentialsProviderBuilder.INVALID_ARGUMENT_MSG, "client id"));
  }

  @Test
  public void shouldFailWithNoClientSecret() {
    // when/then
    assertThatThrownBy(
            () ->
                new OAuthCredentialsProviderBuilder()
                    .audience("a")
                    .clientId("b")
                    .authorizationServerUrl("http://some.url")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(
            String.format(OAuthCredentialsProviderBuilder.INVALID_ARGUMENT_MSG, "client secret"));
  }

  @Test
  public void shouldFailWithMalformedServerUrl() {
    // when/then
    assertThatThrownBy(
            () ->
                new OAuthCredentialsProviderBuilder()
                    .audience("a")
                    .clientId("b")
                    .clientSecret("c")
                    .authorizationServerUrl("someServerUrl")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasCauseInstanceOf(MalformedURLException.class);
  }

  @Test
  public void shouldFailIfSpecifiedCacheIsDir() {
    // given
    final String cachePath = tempFolder.getRoot().getAbsolutePath() + File.separator + "404_folder";
    new File(cachePath).mkdir();

    // when/then
    assertThatThrownBy(
            () ->
                new OAuthCredentialsProviderBuilder()
                    .audience(AUDIENCE)
                    .clientId(CLIENT_ID)
                    .clientSecret(SECRET)
                    .authorizationServerUrl("http://localhost")
                    .credentialsCachePath(cachePath)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Expected specified credentials cache to be a file but found directory instead.");
  }

  @Test
  public void shouldThrowExceptionIfTimeout() {
    // given
    mockCredentials(10_000);
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder
        .usePlaintext()
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(SECRET)
                .audience(AUDIENCE)
                .authorizationServerUrl("http://localhost:" + wireMockRule.port() + "/oauth/token")
                .readTimeout(Duration.ofMillis(5))
                .build())
        .build()
        .close();
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    assertThatThrownBy(() -> client.newTopologyRequest().send().join())
        .hasRootCauseExactlyInstanceOf(SocketTimeoutException.class)
        .hasRootCauseMessage("Read timed out");
  }

  @Test
  public void shouldThrowExceptionIfTimeoutIsZero() {

    // when/then
    assertThatThrownBy(
            () ->
                new ZeebeClientBuilderImpl()
                    .usePlaintext()
                    .credentialsProvider(
                        new OAuthCredentialsProviderBuilder()
                            .clientId(CLIENT_ID)
                            .clientSecret(SECRET)
                            .audience(AUDIENCE)
                            .authorizationServerUrl(
                                "http://localhost:" + wireMockRule.port() + "/oauth/token")
                            .readTimeout(Duration.ZERO)
                            .build())
                    .build()
                    .close())
        .hasMessageContaining(
            "ReadTimeout timeout is 0 milliseconds, "
                + "expected timeout to be a positive number of milliseconds smaller than 2147483647.")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldThrowExceptionIfTimeoutTooLarge() {

    // when/then
    assertThatThrownBy(
            () ->
                new ZeebeClientBuilderImpl()
                    .usePlaintext()
                    .credentialsProvider(
                        new OAuthCredentialsProviderBuilder()
                            .clientId(CLIENT_ID)
                            .clientSecret(SECRET)
                            .audience(AUDIENCE)
                            .authorizationServerUrl(
                                "http://localhost:" + wireMockRule.port() + "/oauth/token")
                            .readTimeout(Duration.ofDays(1_000_000))
                            .build())
                    .build()
                    .close())
        .hasMessageContaining(
            "ReadTimeout timeout is 86400000000000 milliseconds, "
                + "expected timeout to be a positive number of milliseconds smaller than 2147483647.")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldCallOauthServerOnlyOnceInMultithreadMode() throws IOException {
    // given
    mockCredentials(ACCESS_TOKEN);

    final OAuthCredentialsProvider credentialsProvider =
        spy(
            new OAuthCredentialsProviderBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(SECRET)
                .audience(AUDIENCE)
                .authorizationServerUrl("http://localhost:" + wireMockRule.port() + "/oauth/token")
                .credentialsCachePath(tempFolder.newFile().getPath())
                .build());
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder.usePlaintext().credentialsProvider(credentialsProvider);
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    final List<ZeebeFuture<Topology>> responses = new ArrayList<>(10);
    for (int count = 0; count < 10; count++) {
      responses.add(client.newTopologyRequest().send());
    }
    CompletableFuture.allOf(
            responses.stream()
                .map(CompletionStage::toCompletableFuture)
                .collect(Collectors.toList())
                .toArray(new CompletableFuture[] {}))
        .join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
        .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
    verify(1, oauthRequestMatcher());
  }

  /**
   * Mocks an authorization server that returns credentials with the provided access token. Returns
   * the credentials to be return by the server.
   */
  private void mockCredentials(final String accessToken) {
    mockCredentials(accessToken, AUDIENCE);
  }

  private void mockCredentials(final Integer replyDelay) {
    mockCredentials(ACCESS_TOKEN, replyDelay);
  }

  private void mockCredentials(final String accessToken, final Integer replyDelay) {
    mockCredentials(accessToken, AUDIENCE, replyDelay);
  }

  private void mockCredentials(final String accessToken, final String audience) {
    mockCredentials(accessToken, audience, 0);
  }

  private void mockCredentials(
      final String accessToken, final String audience, final Integer replyDelay) {
    final HashMap<String, String> map = new HashMap<>();
    map.put("client_secret", SECRET);
    map.put("client_id", CLIENT_ID);
    map.put("audience", audience);
    map.put("grant_type", "client_credentials");

    final String encodedBody =
        map.entrySet().stream()
            .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(Collectors.joining("&"));

    wireMockRule.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
            .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("User-Agent", matching("zeebe-client-java/\\d+\\.\\d+\\.\\d+.*"))
            .withRequestBody(equalTo(encodedBody))
            .willReturn(
                WireMock.aResponse()
                    .withBody(
                        "{\"access_token\":\""
                            + accessToken
                            + "\",\"token_type\":\""
                            + TOKEN_TYPE
                            + "\",\"expires_in\":"
                            + (EXPIRY.getLong(ChronoField.INSTANT_SECONDS)
                                - Instant.now().getEpochSecond())
                            + ",\"scope\": \""
                            + audience
                            + "\"}")
                    .withFixedDelay(replyDelay)
                    .withStatus(200)));
  }

  private static RequestPatternBuilder oauthRequestMatcher() {
    return postRequestedFor(WireMock.urlPathEqualTo("/oauth/token"));
  }

  private static String encode(final String param) {
    try {
      return URLEncoder.encode(param, StandardCharsets.UTF_8.name());
    } catch (final UnsupportedEncodingException e) {
      throw new UncheckedIOException("Failed while encoding OAuth request parameters: ", e);
    }
  }

  private void assertCacheContents(final String cachePath) throws IOException {
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(new File(cachePath)).readCache();
    final ZeebeClientCredentials credentials =
        new ZeebeClientCredentials(ACCESS_TOKEN, EXPIRY, TOKEN_TYPE);
    assertThat(cache.get(AUDIENCE)).contains(credentials);
  }
}
