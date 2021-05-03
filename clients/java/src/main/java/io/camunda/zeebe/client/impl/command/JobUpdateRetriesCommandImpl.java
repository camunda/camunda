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
import io.zeebe.client.api.command.UpdateRetriesJobCommandStep1;
import io.zeebe.client.api.command.UpdateRetriesJobCommandStep1.UpdateRetriesJobCommandStep2;
import io.zeebe.client.api.response.UpdateRetriesJobResponse;
import io.zeebe.client.impl.RetriableClientFutureImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobRetriesRequest.Builder;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class JobUpdateRetriesCommandImpl
    implements UpdateRetriesJobCommandStep1, UpdateRetriesJobCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Predicate<Throwable> retryPredicate;
  private Duration requestTimeout;

  public JobUpdateRetriesCommandImpl(
      final GatewayStub asyncStub,
      final long jobKey,
      final Duration requestTimeout,
      final Predicate<Throwable> retryPredicate) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    builder = UpdateJobRetriesRequest.newBuilder();
    builder.setJobKey(jobKey);
  }

  @Override
  public UpdateRetriesJobCommandStep2 retries(final int retries) {
    builder.setRetries(retries);
    return this;
  }

  @Override
  public FinalCommandStep<UpdateRetriesJobResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<UpdateRetriesJobResponse> send() {
    final UpdateJobRetriesRequest request = builder.build();

    final RetriableClientFutureImpl<
            UpdateRetriesJobResponse, GatewayOuterClass.UpdateJobRetriesResponse>
        future =
            new RetriableClientFutureImpl<>(
                retryPredicate, streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final UpdateJobRetriesRequest request,
      final StreamObserver<GatewayOuterClass.UpdateJobRetriesResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .updateJobRetries(request, streamObserver);
  }
}
