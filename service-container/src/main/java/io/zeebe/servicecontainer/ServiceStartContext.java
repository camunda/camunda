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

import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;

public interface ServiceStartContext extends ServiceContext {
  String getName();

  ServiceName<?> getServiceName();

  <S> S getService(ServiceName<S> name);

  <S> S getService(String name, Class<S> type);

  <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service);

  CompositeServiceBuilder createComposite(ServiceName<Void> name);

  <S> ActorFuture<Void> removeService(ServiceName<S> name);

  <S> boolean hasService(ServiceName<S> name);

  ActorScheduler getScheduler();

  void async(ActorFuture<?> future, boolean interruptible);

  @Override
  default void async(ActorFuture<?> future) {
    async(future, false);
  }
}
