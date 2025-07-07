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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import org.junit.jupiter.api.Test;

public class SslClientCertAuthTest {

  @Test
  void shouldBuildProviderWithResourceParameterForSslClientCert() {
    // given
    final String resource = "https://api.example.com";
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder
        .clientId("test-client-id")
        .clientSecret("test-client-secret")
        .audience("test-audience")
        .resource(resource)
        .authorizationServerUrl("https://test-issuer.com/oauth/token");

    // then
    assertThatCode(builder::build).doesNotThrowAnyException();
    assertThat(builder.getResource()).isEqualTo(resource);
  }

  @Test
  void shouldSupportResourceParameterForTokenRequests() {
    // given
    final String resource = "urn:example:resource";
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder
        .clientId("client-with-cert")
        .clientSecret("secret")
        .audience("https://auth.example.com/api/v2/")
        .resource(resource)
        .authorizationServerUrl("https://auth.example.com/oauth/token");

    // then
    assertThatCode(builder::build).doesNotThrowAnyException();
    assertThat(builder.getResource()).isEqualTo(resource);
  }

  @Test
  void shouldAllowNullResourceParameter() {
    // given
    final OAuthCredentialsProviderBuilder builder = new OAuthCredentialsProviderBuilder();

    // when
    builder
        .clientId("test-client")
        .clientSecret("test-secret")
        .audience("test-audience")
        .resource(null)
        .authorizationServerUrl("https://test.com/oauth/token");

    // then
    assertThatCode(builder::build).doesNotThrowAnyException();
    assertThat(builder.getResource()).isNull();
  }
}
