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
package io.camunda.process.test.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.auth.scheme.NoAuthAuthScheme;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

class BedrockRuntimeClientFactoryTest {

  private static final String REGION = "us-east-1";
  private static final String API_KEY = "test-api-key";
  private static final String ACCESS_KEY = "test-access-key";
  private static final String SECRET_KEY = "test-secret-key";

  private BedrockRuntimeClientBuilder mockClientBuilder;
  private MockedStatic<BedrockRuntimeClient> staticBedrockRuntimeClient;

  @BeforeEach
  void setUp() {
    mockClientBuilder = mock(BedrockRuntimeClientBuilder.class, Mockito.RETURNS_SELF);
    staticBedrockRuntimeClient = mockStatic(BedrockRuntimeClient.class);
    staticBedrockRuntimeClient.when(BedrockRuntimeClient::builder).thenReturn(mockClientBuilder);
  }

  @AfterEach
  void tearDown() {
    staticBedrockRuntimeClient.close();
  }

  // -- Region configuration --

  @Test
  void shouldConfigureRegionOnClientBuilder() {
    // when
    BedrockRuntimeClientFactory.build(REGION, null, null, null, null);

    // then
    verify(mockClientBuilder).region(Region.of(REGION));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldNotConfigureRegionWhenAbsent(final String region) {
    // when
    BedrockRuntimeClientFactory.build(region, null, null, null, null);

    // then
    verify(mockClientBuilder, never()).region(any());
  }

  // -- Access/secret key authentication --

  @Test
  void shouldConfigureAccessKeyCredentialsOnClientBuilder() {
    // when
    BedrockRuntimeClientFactory.build(REGION, null, ACCESS_KEY, SECRET_KEY, null);

    // then
    final ArgumentCaptor<AwsCredentialsProvider> credentialsCaptor =
        ArgumentCaptor.forClass(AwsCredentialsProvider.class);
    verify(mockClientBuilder).credentialsProvider(credentialsCaptor.capture());

    final AwsCredentials credentials = credentialsCaptor.getValue().resolveCredentials();
    assertThat(credentials.accessKeyId()).isEqualTo(ACCESS_KEY);
    assertThat(credentials.secretAccessKey()).isEqualTo(SECRET_KEY);
  }

  // -- API key (Bearer token) authentication --

  @Test
  void shouldConfigureApiKeyAuthOnClientBuilder() {
    // when
    BedrockRuntimeClientFactory.build(REGION, API_KEY, null, null, null);

    // then
    verify(mockClientBuilder).credentialsProvider(any(AnonymousCredentialsProvider.class));
    verify(mockClientBuilder).putAuthScheme(any(NoAuthAuthScheme.class));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Consumer<ClientOverrideConfiguration.Builder>> overrideCaptor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(mockClientBuilder).overrideConfiguration(overrideCaptor.capture());

    final ClientOverrideConfiguration.Builder overrideBuilder =
        ClientOverrideConfiguration.builder();
    overrideCaptor.getValue().accept(overrideBuilder);
    assertThat(overrideBuilder.build().headers())
        .containsEntry("Authorization", List.of("Bearer " + API_KEY));
  }

  @Test
  void shouldTrimApiKeyInAuthorizationHeader() {
    // when
    BedrockRuntimeClientFactory.build(REGION, "  " + API_KEY + "  ", null, null, null);

    // then
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Consumer<ClientOverrideConfiguration.Builder>> overrideCaptor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(mockClientBuilder).overrideConfiguration(overrideCaptor.capture());

    final ClientOverrideConfiguration.Builder overrideBuilder =
        ClientOverrideConfiguration.builder();
    overrideCaptor.getValue().accept(overrideBuilder);
    assertThat(overrideBuilder.build().headers())
        .containsEntry("Authorization", List.of("Bearer " + API_KEY));
  }

  // -- Timeout configuration --

  @Test
  void shouldConfigureTimeoutWhenSet() {
    // when
    BedrockRuntimeClientFactory.build(REGION, null, null, null, Duration.ofSeconds(30));

    // then
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Consumer<ClientOverrideConfiguration.Builder>> overrideCaptor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(mockClientBuilder).overrideConfiguration(overrideCaptor.capture());

    final ClientOverrideConfiguration.Builder overrideBuilder =
        ClientOverrideConfiguration.builder();
    overrideCaptor.getValue().accept(overrideBuilder);
    assertThat(overrideBuilder.build().apiCallTimeout()).hasValue(Duration.ofSeconds(30));
  }

  @Test
  void shouldUseDefaultTimeoutWhenNull() {
    // when
    BedrockRuntimeClientFactory.build(REGION, null, ACCESS_KEY, SECRET_KEY, null);

    // then
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Consumer<ClientOverrideConfiguration.Builder>> overrideCaptor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(mockClientBuilder).overrideConfiguration(overrideCaptor.capture());

    final ClientOverrideConfiguration.Builder overrideBuilder =
        ClientOverrideConfiguration.builder();
    overrideCaptor.getValue().accept(overrideBuilder);
    assertThat(overrideBuilder.build().apiCallTimeout())
        .hasValue(BedrockRuntimeClientFactory.DEFAULT_TIMEOUT);
  }

  // -- Default credential chain --

  @Test
  void shouldNotConfigureCredentialsWhenNoneProvided() {
    // when
    BedrockRuntimeClientFactory.build(null, null, null, null, null);

    // then
    verify(mockClientBuilder, never()).credentialsProvider(any());
    verify(mockClientBuilder, never()).putAuthScheme(any());
  }

  // -- Error cases --

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenOnlyAccessKeyProvided(final String secretKey) {
    assertThatThrownBy(
            () -> BedrockRuntimeClientFactory.build(REGION, null, ACCESS_KEY, secretKey, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accessKey")
        .hasMessageContaining("secretKey");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void shouldThrowWhenOnlySecretKeyProvided(final String accessKey) {
    assertThatThrownBy(
            () -> BedrockRuntimeClientFactory.build(REGION, null, accessKey, SECRET_KEY, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accessKey")
        .hasMessageContaining("secretKey");
  }

  @Test
  void shouldThrowWhenBothAuthMethodsProvided() {
    assertThatThrownBy(
            () -> BedrockRuntimeClientFactory.build(REGION, API_KEY, ACCESS_KEY, SECRET_KEY, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("amazon-bedrock");
  }
}
