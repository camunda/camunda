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
package io.camunda.client.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.gateway.protocol.GrpcHeaders;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class PhysicalTenantInterceptorTest {

  @Test
  @SuppressWarnings("unchecked")
  void shouldSetHeaderForConfiguredTenant() {
    // given
    final PhysicalTenantInterceptor interceptor = new PhysicalTenantInterceptor("tenant-x");
    final AtomicReference<Metadata> capturedHeaders = new AtomicReference<>();
    final Channel channel = channelReturning(new CapturingClientCall<>(capturedHeaders));

    // when
    final ClientCall<Object, Object> call =
        interceptor.interceptCall(mock(MethodDescriptor.class), CallOptions.DEFAULT, channel);
    call.start(new NoopListener<>(), new Metadata());

    // then
    assertThat(capturedHeaders.get().get(GrpcHeaders.PHYSICAL_TENANT)).isEqualTo("tenant-x");
  }

  @Test
  void shouldThrowWhenTenantIdIsNull() {
    assertThatThrownBy(() -> new PhysicalTenantInterceptor(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("physicalTenantId must not be null");
  }

  @SuppressWarnings({"rawtypes"})
  private static Channel channelReturning(final ClientCall clientCall) {
    final Channel channel = mock(Channel.class);
    doReturn(clientCall).when(channel).newCall(any(), any());
    return channel;
  }

  private static final class CapturingClientCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {
    private final AtomicReference<Metadata> capture;

    CapturingClientCall(final AtomicReference<Metadata> capture) {
      this.capture = capture;
    }

    @Override
    public void start(final Listener<RespT> responseListener, final Metadata headers) {
      capture.set(headers);
    }

    @Override
    public void request(final int numMessages) {}

    @Override
    public void cancel(final String message, final Throwable cause) {}

    @Override
    public void halfClose() {}

    @Override
    public void sendMessage(final ReqT message) {}
  }

  private static final class NoopListener<RespT> extends ClientCall.Listener<RespT> {}
}
