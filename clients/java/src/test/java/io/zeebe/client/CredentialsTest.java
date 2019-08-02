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

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerInterceptors;
import io.grpc.testing.GrpcServerRule;
import io.zeebe.client.impl.ZeebeClientBuilderImpl;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.client.util.RecordingGatewayService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CredentialsTest {
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

    recordingInterceptor.reset();
  }

  @Test
  public void shouldModifyCallMetadata() {
    // given
    final Key<String> key = Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    final String bearerToken = "Bearer someToken";
    final ZeebeClientBuilderImpl builder = new ZeebeClientBuilderImpl();

    builder.usePlaintext().credentialsProvider(headers -> headers.put(key, bearerToken));
    client = new ZeebeClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().get(key)).isEqualTo(bearerToken);
  }
}
