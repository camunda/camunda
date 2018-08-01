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
package io.zeebe.logstreams.impl.service;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.future.ActorFuture;

public class LogWriteBufferSubscriptionService implements Service<Subscription> {
  private final Injector<Dispatcher> logWritebufferInjector = new Injector<>();

  private final String subscriptionName;

  private ActorFuture<Subscription> subscriptionFuture;

  public LogWriteBufferSubscriptionService(String subscriptionName) {
    this.subscriptionName = subscriptionName;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    final Dispatcher logBuffer = logWritebufferInjector.getValue();

    subscriptionFuture = logBuffer.openSubscriptionAsync(subscriptionName);
    startContext.async(subscriptionFuture);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    final Dispatcher logBuffer = logWritebufferInjector.getValue();

    stopContext.async(logBuffer.closeSubscriptionAsync(subscriptionFuture.join()));
  }

  @Override
  public Subscription get() {
    return subscriptionFuture.join();
  }

  public Injector<Dispatcher> getWritebufferInjector() {
    return logWritebufferInjector;
  }
}
