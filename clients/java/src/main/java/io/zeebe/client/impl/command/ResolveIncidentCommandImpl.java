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
package io.zeebe.client.impl.command;

import io.grpc.stub.StreamObserver;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.command.ResolveIncidentCommandStep1;
import io.zeebe.client.api.response.ResolveIncidentResponse;
import io.zeebe.client.impl.RetriableClientFutureImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest.Builder;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class ResolveIncidentCommandImpl implements ResolveIncidentCommandStep1 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Predicate<Throwable> retryPredicate;
  private Duration requestTimeout;

  public ResolveIncidentCommandImpl(
      final GatewayStub asyncStub,
      final long incidentKey,
      final Duration requestTimeout,
      final Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    builder = ResolveIncidentRequest.newBuilder().setIncidentKey(incidentKey);
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
  }

  @Override
  public FinalCommandStep<ResolveIncidentResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<ResolveIncidentResponse> send() {
    final ResolveIncidentRequest request = builder.build();

    final RetriableClientFutureImpl<
            ResolveIncidentResponse, GatewayOuterClass.ResolveIncidentResponse>
        future =
            new RetriableClientFutureImpl<>(
                retryPredicate, streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final ResolveIncidentRequest request,
      final StreamObserver<GatewayOuterClass.ResolveIncidentResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .resolveIncident(request, streamObserver);
  }
}
