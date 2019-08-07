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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerInterceptors;
import io.grpc.testing.GrpcServerRule;
import io.zeebe.client.impl.OAuthCredentialsProvider;
import io.zeebe.client.impl.OAuthCredentialsProviderBuilder;
import io.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.util.RecordingGatewayService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class OAuthCredentialsProviderTest {
  @Rule public final GrpcServerRule serverRule = new GrpcServerRule();

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
  }

  @Test
  public void shouldModifyCallHeaders() throws IOException {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();
    final OAuthCredentialsProviderBuilder credsProviderBuilder =
        Mockito.spy(
            new OAuthCredentialsProviderBuilder()
                .clientId("id")
                .clientSecret("secret")
                .audience("endpoint"));
    final HttpURLConnection connection = mockAuthServerConnection(credsProviderBuilder);

    builder.usePlaintext().credentialsProvider(new OAuthCredentialsProvider(credsProviderBuilder));
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    final Key<String> key = Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    assertThat(recordingInterceptor.getCapturedHeaders().get(key)).isEqualTo("Bearer someToken");

    final ByteArrayOutputStream outputStream = (ByteArrayOutputStream) connection.getOutputStream();
    final String jsonPayload = new String(outputStream.toByteArray());
    final Map<String, String> payload =
        new ObjectMapper().readerFor(Map.class).readValue(jsonPayload);
    assertThat(payload)
        .containsEntry("client_id", "id")
        .containsEntry("client_secret", "secret")
        .containsEntry("audience", "endpoint");
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

  private HttpURLConnection mockAuthServerConnection(OAuthCredentialsProviderBuilder builder)
      throws IOException {
    final String jsonResponse = "{\"access_token\":\"someToken\",\"token_type\":\"Bearer\"}";

    final URL authServerUrl = Mockito.spy(new URL("https://authServerUrl"));
    final HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
    Mockito.when(urlConnection.getResponseCode()).thenReturn(200);
    Mockito.when(urlConnection.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    Mockito.when(urlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8)));

    Mockito.when(authServerUrl.openConnection()).thenReturn(urlConnection);
    Mockito.when(builder.getAuthorizationServer()).thenReturn(authServerUrl);
    return urlConnection;
  }
}
