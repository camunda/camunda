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
  private ServiceContainerImpl serviceContainer;
  private final boolean shouldStop;

  private ActorSchedulerRule actorSchedulerRule;
  private ControlledActorSchedulerRule controlledActorSchedulerRule;

  public ServiceContainerRule(ActorSchedulerRule actorSchedulerRule, boolean shouldStop) {
    this.actorSchedulerRule = actorSchedulerRule;
    this.shouldStop = shouldStop;
  }

  public ServiceContainerRule(
      ControlledActorSchedulerRule controlledActorSchedulerRule, boolean shouldStop) {
    this.controlledActorSchedulerRule = controlledActorSchedulerRule;
    this.shouldStop = shouldStop;
  }

  public ServiceContainerRule(ActorSchedulerRule actorSchedulerRule) {
    this(actorSchedulerRule, true);
  }

  public ServiceContainerRule(ControlledActorSchedulerRule actorSchedulerRule) {
    this(actorSchedulerRule, false);
  }

  @Override
  protected void before() throws Throwable {
    final ActorScheduler actorScheduler =
        actorSchedulerRule == null ? controlledActorSchedulerRule.get() : actorSchedulerRule.get();
    serviceContainer = new ServiceContainerImpl(actorScheduler);
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

  public ActorSchedulerRule getActorSchedulerRule() {
    return actorSchedulerRule;
  }
}
