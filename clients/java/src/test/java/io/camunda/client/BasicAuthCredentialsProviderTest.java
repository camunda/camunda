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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.impl.HttpStatusCode;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProvider;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.client.util.TestCredentialsApplier;
import io.camunda.client.util.TestCredentialsApplier.Credential;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class BasicAuthCredentialsProviderTest {

  private final TestCredentialsApplier applier = new TestCredentialsApplier();

  @Test
  void shouldEncodeCredentialsAndAddToCall() {
    // given
    final String username = UUID.randomUUID().toString();
    final String password = UUID.randomUUID().toString();
    final BasicAuthCredentialsProvider provider =
        new BasicAuthCredentialsProviderBuilder().username(username).password(password).build();

    // when
    provider.applyCredentials(applier);

    // then
    assertThat(applier.getCredentials())
        .containsExactly(
            new Credential(
                "Authorization", "Basic " + base64EncodeCredentials(username, password)));
  }

  @Test
  void shouldNotRetryRequest() {
    // given
    final BasicAuthCredentialsProvider provider =
        new BasicAuthCredentialsProviderBuilder().username("username").password("password").build();

    // when
    final boolean shouldRetryRequest = provider.shouldRetryRequest(new HttpStatusCode(401));

    // then
    assertThat(shouldRetryRequest).isFalse();
  }

  private String base64EncodeCredentials(final String username, final String password) {
    return Base64.getEncoder()
        .encodeToString(String.format("%s:%s", username, password).getBytes());
  }
}
