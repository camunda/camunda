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

import static io.zeebe.client.impl.ZeebeClientCredentialsProvider.INVALID_PATH_ERROR_MSG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerInterceptors;
import io.grpc.testing.GrpcServerRule;
import io.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.zeebe.client.impl.ZeebeClientCredentialsProvider;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.util.RecordingGatewayService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ZeebeClientCredentialsProviderTest {

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
  public void shouldModifyCallHeaders() {
    // given
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    builder
        .usePlaintext()
        .credentialsProvider(
            new ZeebeClientCredentialsProvider(
                this.getClass().getClassLoader().getResource("creds").getPath()));
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    final Key<String> key = Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    assertThat(recordingInterceptor.getCapturedHeaders().get(key)).isEqualTo("Bearer someToken");
  }

  @Test
  public void shouldFailWithNullPath() {
    // when/then
    assertThatThrownBy(() -> new ZeebeClientCredentialsProvider(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(INVALID_PATH_ERROR_MSG.replaceFirst("%s", "null"));
  }

  @Test
  public void shouldFailWithEmptyPath() {
    // when/then
    assertThatThrownBy(() -> new ZeebeClientCredentialsProvider(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageMatching(INVALID_PATH_ERROR_MSG.replaceFirst("%s", ""));
  }

  @Test
  public void shouldFailWithNonExistingFile() {
    // given
    final String badCredentialsPath =
        this.getClass().getClassLoader().getResource("creds").getPath() + "_fail";

    // when/then
    assertThatThrownBy(() -> new ZeebeClientCredentialsProvider(badCredentialsPath))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageMatching(INVALID_PATH_ERROR_MSG.replaceFirst("%s", badCredentialsPath));
  }
}
