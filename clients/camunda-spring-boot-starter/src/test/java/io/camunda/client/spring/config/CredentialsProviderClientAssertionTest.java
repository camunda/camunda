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
package io.camunda.client.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.client.jobhandling.CamundaClientExecutorService;
import io.camunda.client.spring.configuration.CredentialsProviderConfiguration;
import io.camunda.client.spring.properties.CamundaClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {CredentialsProviderConfiguration.class},
    properties = {
      "camunda.client.mode=self-managed",
      "camunda.client.auth.client-id=CredentialsProviderSelfManagedTest-my-client-id",
      "camunda.client.auth.client-secret=my-client-secret",
      "camunda.client.auth.client-assertion.keystore-password=mstest"
    })
@EnableConfigurationProperties(CamundaClientProperties.class)
public class CredentialsProviderClientAssertionTest {
  @Autowired CredentialsProvider credentialsProvider;
  @MockitoBean CamundaClientExecutorService camundaClientExecutorService;

  @DynamicPropertySource
  static void properties(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.client.auth.client-assertion.keystore-path",
        () ->
            CredentialsProviderClientAssertionTest.class
                .getClassLoader()
                .getResource("keystore/test.jks")
                .getPath());
  }

  @Test
  void shouldBuildCredentialsProviderWithClientAssertion() {
    assertThat(credentialsProvider).isInstanceOf(OAuthCredentialsProvider.class);
    final OAuthCredentialsProvider oAuthCredentialsProvider =
        (OAuthCredentialsProvider) credentialsProvider;
    assertThat(oAuthCredentialsProvider.isClientAssertionEnabled()).isTrue();
  }
}
