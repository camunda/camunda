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
package io.camunda.client.spring.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProvider;
import io.camunda.client.spring.properties.CamundaClientProperties;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {CredentialsProviderConfiguration.class},
    properties = {
      "camunda.client.auth.method=basic",
      "camunda.client.auth.username=foo",
      "camunda.client.auth.password=bar"
    })
@EnableConfigurationProperties({CamundaClientProperties.class})
public class CredentialsProviderBasicTest {
  public static final String USERNAME = "foo";
  public static final String PASSWORD = "bar";
  @Autowired CredentialsProvider credentialsProvider;

  @Test
  void shouldCreateBasicAuthCredentialsProvider() {
    assertThat(credentialsProvider).isExactlyInstanceOf(BasicAuthCredentialsProvider.class);
  }

  @Test
  void shouldHaveBasicAuthHeader() throws IOException {
    final Map<String, String> headers = new HashMap<>();
    final var encodedCredentials =
        Base64.getEncoder().encodeToString(String.format("%s:%s", USERNAME, PASSWORD).getBytes());

    credentialsProvider.applyCredentials(headers::put);
    assertThat(headers).isEqualTo(Map.of("Authorization", "Basic " + encodedCredentials));
  }
}
