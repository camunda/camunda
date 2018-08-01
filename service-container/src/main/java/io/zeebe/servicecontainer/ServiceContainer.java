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
package io.zeebe.servicecontainer;

import io.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface ServiceContainer {
  void start();

  boolean hasService(ServiceName<?> name);

  <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service);

  CompositeServiceBuilder createComposite(ServiceName<Void> name);

  ActorFuture<Void> removeService(ServiceName<?> serviceName);

  void close(long awaitTime, TimeUnit timeUnit)
      throws TimeoutException, ExecutionException, InterruptedException;

  ActorFuture<Void> closeAsync();
}
