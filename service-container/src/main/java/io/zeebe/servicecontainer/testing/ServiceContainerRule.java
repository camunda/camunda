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
package io.zeebe.servicecontainer.testing;

import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.rules.ExternalResource;

public class ServiceContainerRule extends ExternalResource {
  private final ServiceContainerImpl serviceContainer;
  private ActorScheduler actorScheduler;
  private boolean shouldStop;

  public ServiceContainerRule(ActorScheduler actorScheduler, boolean shouldStop) {
    this.actorScheduler = actorScheduler;
    this.shouldStop = shouldStop;
    this.serviceContainer = new ServiceContainerImpl(actorScheduler);
  }

  public ServiceContainerRule(ActorScheduler actorScheduler) {
    this(actorScheduler, true);
  }

  public ServiceContainerRule(ActorSchedulerRule actorSchedulerRule) {
    this(actorSchedulerRule.get(), true);
  }

  public ServiceContainerRule(ControlledActorSchedulerRule actorSchedulerRule) {
    this(actorSchedulerRule.get(), false);
  }

  @Override
  protected void before() throws Throwable {
    serviceContainer.start();
  }

  @Override
  protected void after() {
    if (shouldStop) {
      try {
        serviceContainer.close(5, TimeUnit.SECONDS);
      } catch (InterruptedException | ExecutionException | TimeoutException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public ServiceContainer get() {
    return serviceContainer;
  }

  public ActorScheduler getActorScheduler() {
    return actorScheduler;
  }
}
