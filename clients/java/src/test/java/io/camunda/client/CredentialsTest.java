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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

import io.camunda.client.CredentialsProvider.StatusCode;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import io.camunda.client.impl.CamundaClientImpl;
import io.camunda.client.util.RecordingGatewayService;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.internal.GrpcUtil;
import io.grpc.testing.GrpcServerRule;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class CredentialsTest {
  private static final Key<String> AUTH_KEY =
      Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  @Rule public final GrpcServerRule serverRule = new GrpcServerRule();

  private final RecordingInterceptor recordingInterceptor = new RecordingInterceptor();
  private final RecordingGatewayService gatewayService = new RecordingGatewayService();
  private CamundaClient client;
  private final CamundaClientBuilderImpl builder = new CamundaClientBuilderImpl();

  @Before
  public void setUp() {
    serverRule
        .getServiceRegistry()
        .addService(ServerInterceptors.intercept(gatewayService, recordingInterceptor));

    builder.preferRestOverGrpc(false);
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
  public void shouldAddTokenToCallHeaders() {
    // given
    final String bearerToken = "Bearer someToken";

    builder.credentialsProvider(
        new CredentialsProvider() {
          @Override
          public void applyCredentials(final CredentialsApplier applier) {
            applier.put("Authorization", bearerToken);
          }

          @Override
          public boolean shouldRetryRequest(final StatusCode statusCode) {
            return false;
          }
        });
    client = new CamundaClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY)).isEqualTo(bearerToken);
  }

  @Test
  public void shouldRetryRequest() {
    // given
    recordingInterceptor.setInterceptAction(
        (call, headers) -> {
          recordingInterceptor.reset();
          call.close(Status.UNKNOWN, headers);
        });

    final CredentialsProvider provider =
        Mockito.spy(
            new CredentialsProvider() {
              int attempt = 0;

              @Override
              public void applyCredentials(final CredentialsApplier applier) {
                applier.put("Authorization", String.format("Bearer token-%d", attempt++));
              }

              @Override
              public boolean shouldRetryRequest(final StatusCode statusCode) {
                return true;
              }
            });
    builder.credentialsProvider(provider);
    client = new CamundaClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    Mockito.verify(provider, times(1)).shouldRetryRequest(any(StatusCode.class));
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY)).isEqualTo("Bearer token-1");
  }

  @Test
  public void shouldRetryMoreThanOnce() {
    // given
    final int retries = 2;

    recordingInterceptor.setInterceptAction((call, headers) -> call.close(Status.UNKNOWN, headers));

    final CredentialsProvider provider =
        Mockito.spy(
            new CredentialsProvider() {
              int retryCounter = retries;

              @Override
              public void applyCredentials(final CredentialsApplier applier) {
                applier.put("Authorization", String.format("Bearer token-%d", retryCounter));
              }

              @Override
              public boolean shouldRetryRequest(final StatusCode code) {
                retryCounter--;
                return true;
              }
            });

    builder.credentialsProvider(provider);
    client = new CamundaClientImpl(builder, serverRule.getChannel());

    // when/then
    assertThatThrownBy(() -> client.newTopologyRequest().send().join())
        .isInstanceOf(ClientException.class);

    Mockito.verify(provider, times(retries)).shouldRetryRequest(any(StatusCode.class));
    assertThat(recordingInterceptor.getCapturedHeaders().get(AUTH_KEY)).isEqualTo("Bearer token-0");
  }

  @Test
  public void shouldNotChangeHeadersWithNoProvider() {
    // given
    client = new CamundaClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    assertThat(recordingInterceptor.getCapturedHeaders().containsKey(AUTH_KEY)).isFalse();
  }

  @Test
  public void shouldCredentialsProviderRunFromGRPCThreadPool() {
    // given
    final AtomicReference<String> credentialsProviderThreadReference = new AtomicReference<>();
    builder.credentialsProvider(
        new CredentialsProvider() {
          @Override
          public void applyCredentials(final CredentialsApplier ignored) {
            credentialsProviderThreadReference.set(Thread.currentThread().getName());
          }

          @Override
          public boolean shouldRetryRequest(final StatusCode statusCode) {
            return false;
          }
        });
    client = new CamundaClientImpl(builder, serverRule.getChannel());

    // when
    client.newTopologyRequest().send().join();

    // then
    assertThat(credentialsProviderThreadReference)
        .hasValueMatching(
            s -> s.startsWith(GrpcUtil.SHARED_CHANNEL_EXECUTOR.toString()),
            "should be the GRPC's thread");
  }
}
