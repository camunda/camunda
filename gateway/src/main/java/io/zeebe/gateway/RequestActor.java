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
package io.zeebe.gateway;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class RequestActor extends Actor {

  public <V, R> void handleResponse(
      final ActorFuture<V> responseFuture,
      final Function<V, R> responseMapper,
      final StreamObserver<R> streamObserver) {
    actor.call(
        () ->
            actor.runOnCompletion(
                responseFuture,
                (v, t) -> {
                  if (t == null) {
                    try {
                      final R response = responseMapper.apply(v);
                      streamObserver.onNext(response);
                      streamObserver.onCompleted();
                    } catch (Exception e) {
                      streamObserver.onError(convertThrowable(e));
                    }
                  } else {
                    streamObserver.onError(convertThrowable(t));
                  }
                }));
  }

  private StatusRuntimeException convertThrowable(final Throwable cause) {
    final String description;

    if (cause instanceof ExecutionException) {
      description = cause.getCause().getMessage();
    } else {
      description = cause.getMessage();
    }

    return Status.INTERNAL.augmentDescription(description).asRuntimeException();
  }
}
