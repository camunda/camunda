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
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerCall;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.testing.GrpcServerRule;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.impl.OAuthCredentialsProviderBuilder;
import io.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.util.RecordingGatewayService;
import java.net.MalformedURLException;
import java.time.Duration;
import java.util.function.BiConsumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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

  @Rule
  public final WireMockRule wireMockRule =
      new WireMockRule(wireMockConfig().dynamicPort().dynamicPort());

  private final RecordingInterceptor recordingInterceptor = new RecordingInterceptor();
  private final RecordingGatewayService gatewayService = new RecordingGatewayService();
  private ZeebeClient client;

  @Before
  public void setUp() {
    serverRule
        .getServiceRegistry()
        .addService(ServerInterceptors.intercept(gatewayService, recordingInterceptor));
  }

  @After
  public void tearDown() {
    if (client != null) {
      client.close();
      client = null;
    }

    recordingInterceptor.reset();
  }

  @Test
  public void shouldRequestTokenAndAddToCall() {
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
                .build());
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
        .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
  }

  @Test
  public void shouldRetryRequestWithNewCredentials() {
    // given
    final String firstToken = "firstToken";
    mockCredentials(firstToken);
    final BiConsumer<ServerCall, Metadata> interceptAction =
        Mockito.spy(
            new BiConsumer<ServerCall, Metadata>() {
              @Override
              public void accept(ServerCall call, Metadata headers) {
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
                .build());

    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    final ArgumentCaptor<Metadata> captor = ArgumentCaptor.forClass(Metadata.class);

    Mockito.verify(interceptAction, times(1)).accept(any(ServerCall.class), captor.capture());
    assertThat(captor.getValue().get(AUTH_KEY)).isEqualTo(TOKEN_TYPE + " " + firstToken);
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY))
        .isEqualTo(TOKEN_TYPE + " " + ACCESS_TOKEN);
  }

  @Test
  public void shouldNotRetryWithSameCredentials() {
    // given
    mockCredentials(ACCESS_TOKEN);
    final BiConsumer<ServerCall, Metadata> interceptAction =
        Mockito.spy(
            new BiConsumer<ServerCall, Metadata>() {
              @Override
              public void accept(ServerCall call, Metadata headers) {
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
                .build());

    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    assertThatThrownBy(() -> client.newTopologyRequest().send().join())
        .isInstanceOf(ClientException.class);
    Mockito.verify(interceptAction, times(1)).accept(any(ServerCall.class), any(Metadata.class));
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
  public void shouldFailWithNoAuthServerUrl() {
    // when/then
    assertThatThrownBy(
            () ->
                new OAuthCredentialsProviderBuilder()
                    .audience("a")
                    .clientId("b")
                    .clientSecret("c")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageEndingWith(
            String.format(
                OAuthCredentialsProviderBuilder.INVALID_ARGUMENT_MSG, "authorization server URL"));
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

  private void mockCredentials(final String accessToken) {
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
                        + AUDIENCE
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
}
