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
package io.camunda.zeebe.client.impl;

import io.camunda.zeebe.client.CredentialsProvider.StatusCode;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.stub.StreamObserver;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class RetriableStreamingFutureImpl<C, B> extends ZeebeStreamingClientFutureImpl<C, B> {

  private final Predicate<StatusCode> retryPredicate;
  private final Consumer<StreamObserver<B>> retryAction;

  public RetriableStreamingFutureImpl(
      final C clientResponse,
      final Consumer<B> collector,
      final Predicate<StatusCode> retryPredicate,
      final Consumer<StreamObserver<B>> retryAction) {
    super(clientResponse, collector);

    Objects.requireNonNull(retryPredicate, "Expected to have non-null retry predicate.");
    Objects.requireNonNull(retryAction, "Expected to have non-null retry action.");
    this.retryPredicate = retryPredicate;
    this.retryAction = retryAction;
  }

  @Override
  public void onError(final Throwable throwable) {
    final Status status = Status.fromThrowable(throwable);

    // if the throwable was not a status exception (i.e. it has no code), then UNKNOWN is used, so
    // we can skip calling the predicate here
    if (status.getCode() != Code.UNKNOWN
        && retryPredicate.test(new GrpcStatusCode(status.getCode()))) {
      retryAction.accept(this);
    } else {
      super.onError(throwable);
    }
  }
}
