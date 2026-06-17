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

import io.camunda.zeebe.gateway.protocol.GrpcHeaders;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.util.Objects;

/**
 * Sets the {@code Camunda-Physical-Tenant} gRPC metadata header on every outgoing call so the
 * gateway can route the request to the correct physical tenant partition.
 */
public final class PhysicalTenantInterceptor implements ClientInterceptor {

  private final String physicalTenantId;

  public PhysicalTenantInterceptor(final String physicalTenantId) {
    this.physicalTenantId =
        Objects.requireNonNull(physicalTenantId, "physicalTenantId must not be null");
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      final MethodDescriptor<ReqT, RespT> method,
      final CallOptions callOptions,
      final Channel next) {
    return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
      @Override
      public void start(final Listener<RespT> responseListener, final Metadata headers) {
        headers.put(GrpcHeaders.PHYSICAL_TENANT, physicalTenantId);
        super.start(responseListener, headers);
      }
    };
  }
}
