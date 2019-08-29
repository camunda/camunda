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
package io.zeebe.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerCall;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.testing.GrpcServerRule;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.zeebe.client.impl.ZeebeClientCredentials;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.impl.oauth.OAuthCredentialsCache;
import io.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.zeebe.client.util.RecordingGatewayService;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.time.Duration;
import java.util.function.BiConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class OAuthCredentialsProviderTest {

  private static final Key<String> AUTH_KEY =
      Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
  private static final String SCOPE = "grpc";
  private static final long EXPIRES_IN = Duration.ofDays(1).getSeconds();
  private static final String SECRET = "secret";
  private static final String AUDIENCE = "endpoint";
  private static final String ACCESS_TOKEN = "someToken";
  private static final String TOKEN_TYPE = "Bearer";
  private static final String CLIENT_ID = "client";
  @Rule public final GrpcServerRule serverRule = new GrpcServerRule();
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  @Rule
  public final WireMockRule wireMockRule =
      new WireMockRule(wireMockConfig().dynamicPort().dynamicPort());

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
                .build());
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
                .build());

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
                .build());

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
    mockCredentials(ACCESS_TOKEN, contactPointHost);

    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    builder
        .usePlaintext()
        .brokerContactPoint(contactPointHost + ":26500")
        .oAuthCredentialsProvider(
            CLIENT_ID, SECRET, "http://localhost:" + wireMockRule.port() + "/oauth/token")
        .build();

    // when
    client = new ZeebeClientImpl(builder, serverRule.getChannel());
    client.newTopologyRequest().send().join();

    // when
    WireMock.verify(
        WireMock.postRequestedFor(WireMock.urlPathEqualTo("/oauth/token"))
            .withRequestBody(
                equalToJson(
                    "{\"client_secret\":\""
                        + SECRET
                        + "\",\"client_id\":\""
                        + CLIENT_ID
                        + "\",\"audience\": \""
                        + contactPointHost
                        + "\",\"grant_type\": \"client_credentials\"}")));
  }

  @Test
  public void shouldUseCachedCredentials() throws IOException {
    // given
    mockCredentials(ACCESS_TOKEN);
    final String cachePath = tempFolder.getRoot().getPath() + File.separator + ".credsCache";
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(new File(cachePath));
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    cache
        .put(AUDIENCE, new ZeebeClientCredentials(ACCESS_TOKEN, EXPIRES_IN, TOKEN_TYPE, SCOPE))
        .writeCache();
    builder
        .usePlaintext()
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(CLIENT_ID)
                .clientSecret(SECRET)
                .audience(AUDIENCE)
                .authorizationServerUrl("http://localhost:" + wireMockRule.port() + "/oauth/token")
                .credentialsCachePath(cachePath)
                .build());
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
        .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
    verify(0, postRequestedFor(WireMock.urlPathEqualTo("/oauth/token")));
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
    builder.usePlaintext().credentialsProvider(credsBuilder.build());
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();
    verify(1, postRequestedFor(WireMock.urlPathEqualTo("/oauth/token")));

    builder.usePlaintext().credentialsProvider(credsBuilder.build());
    client = new ZeebeClientImpl(builder, serverRule.getChannel());
    client.newTopologyRequest().send().join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
        .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
    verify(1, postRequestedFor(WireMock.urlPathEqualTo("/oauth/token")));
    assertCacheContents(cachePath);
  }

  @Test
  public void shouldUpdateCacheIfStale() throws IOException {
    // given
    mockCredentials(ACCESS_TOKEN);
    recordingInterceptor.setInterceptAction(
        new BiConsumer<ServerCall, Metadata>() {
          @Override
          public void accept(final ServerCall call, final Metadata metadata) {
            final String authHeader = metadata.get(AUTH_KEY);
            if (authHeader != null && authHeader.endsWith("staleToken")) {
              call.close(Status.UNAUTHENTICATED, metadata);
            }
          }
        });
    final String cachePath = tempFolder.getRoot().getPath() + File.separator + ".credsCache";
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(new File(cachePath));
    cache
        .put(AUDIENCE, new ZeebeClientCredentials("staleToken", EXPIRES_IN, TOKEN_TYPE, SCOPE))
        .writeCache();

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
                .build());
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
        .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
    verify(1, postRequestedFor(WireMock.urlPathEqualTo("/oauth/token")));
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

  /**
   * Mocks an authorization server that returns credentials with the provided access token. Returns
   * the credentials to be return by the server.
   */
  private void mockCredentials(final String accessToken) {
    mockCredentials(accessToken, AUDIENCE);
  }

  private void mockCredentials(final String accessToken, final String audience) {
    wireMockRule.stubFor(
        WireMock.post(WireMock.urlPathEqualTo("/oauth/token"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(
                equalToJson(
                    "{\"client_secret\":\""
                        + SECRET
                        + "\",\"client_id\":\""
                        + CLIENT_ID
                        + "\",\"audience\": \""
                        + audience
                        + "\",\"grant_type\": \"client_credentials\"}"))
            .willReturn(
                WireMock.okJson(
                    "{\"access_token\":\""
                        + accessToken
                        + "\",\"token_type\":\""
                        + TOKEN_TYPE
                        + "\",\"expires_in\":"
                        + EXPIRES_IN
                        + ",\"scope\":\""
                        + SCOPE
                        + "\"}")));
  }

  private void assertCacheContents(final String cachePath) throws IOException {
    final OAuthCredentialsCache cache = new OAuthCredentialsCache(new File(cachePath)).readCache();
    final ZeebeClientCredentials credentials =
        new ZeebeClientCredentials(ACCESS_TOKEN, EXPIRES_IN, TOKEN_TYPE, SCOPE);
    assertThat(cache.get(AUDIENCE)).contains(credentials);
  }
}
