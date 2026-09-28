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
package io.camunda.client.impl.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.impl.CamundaClientBuilderImpl;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpClientFactoryTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldSetConnectionLifetimeForEveryRestConnection(final boolean clientSideLoadBalancing) {
    // given
    final CamundaClientBuilderImpl config = new CamundaClientBuilderImpl();
    config.useClientSideLoadBalancing(clientSideLoadBalancing);

    // when
    final ConnectionConfig connectionConfig =
        new HttpClientFactory(config)
            .createConnectionConfig(new HttpRoute(new HttpHost("http", "localhost", 8080)));
    // then
    assertThat(connectionConfig.getTimeToLive().toSeconds())
        .isEqualTo(clientSideLoadBalancing ? 1 : 60);
    assertThat(connectionConfig.getValidateAfterInactivity().toSeconds()).isEqualTo(5);
  }
}
