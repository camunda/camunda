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

import io.grpc.stub.StreamObserver;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.function.Function;

public class RequestActor extends Actor {

  public <V, R> void handleResponse(
      ActorFuture<V> responseFuture,
      Function<V, R> responseMapper,
      StreamObserver<R> streamObserver) {
    actor.call(
        () ->
            actor.runOnCompletion(
                responseFuture,
                (v, t) -> {
                  try {
                    if (t == null) {
                      final R response = responseMapper.apply(v);
                      streamObserver.onNext(response);
                    } else {
                      streamObserver.onError(t);
                    }
                  } catch (Exception e) {
                    streamObserver.onError(e);
                  } finally {
                    streamObserver.onCompleted();
                  }
                }));
  }
}
