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
package io.camunda.zeebe.spring.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.spring.client.configuration.JsonMapperConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientConfigurationImpl;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    classes = {
      JsonMapperConfiguration.class,
      ZeebeClientConfigurationImpl.class,
    },
    properties = {
      "camunda.client.mode=self-managed",
      "camunda.client.auth.client-id=CredentialsProviderSelfManagedTest-my-client-id",
      "camunda.client.auth.client-secret=my-client-secret",
      "camunda.client.auth.client-assertion.keystore-password=mstest"
    })
@EnableConfigurationProperties({
  CamundaClientProperties.class,
  ZeebeClientConfigurationProperties.class
})
public class CredentialsProviderClientAssertionTest {
  @MockitoBean ZeebeClientExecutorService zeebeClientExecutorService;
  @Autowired ZeebeClientConfigurationImpl zeebeClientConfiguration;

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
    assertThat(zeebeClientConfiguration.getCredentialsProvider())
        .isInstanceOf(OAuthCredentialsProvider.class);
    final OAuthCredentialsProvider oAuthCredentialsProvider =
        (OAuthCredentialsProvider) zeebeClientConfiguration.getCredentialsProvider();
    assertThat(oAuthCredentialsProvider.isClientAssertionEnabled()).isTrue();
  }
}
