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
package io.zeebe.servicecontainer.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceBuilder;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ServiceContainerIntegrationTest {
  final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
  final ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);

  @Rule
  public final RuleChain ruleChain =
      RuleChain.outerRule(actorSchedulerRule).around(serviceContainerRule);

  @Test
  public void testInstall() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    final List<ActorFuture<Void>> futures = new ArrayList<>();

    for (int i = 0; i < 1_000; i++) {
      final ServiceBuilder<Void> builder =
          serviceContainer.createService(
              ServiceName.newServiceName("service" + i, Void.class), new TestService());

      if (i < (1_000 - 1)) {
        builder.dependency(ServiceName.newServiceName("service" + (i + 1), Void.class));
      }

      futures.add(builder.install());
    }

    futures.forEach((f) -> f.join());
  }

  @Test
  public void testInstallAsync() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    final List<ActorFuture<Void>> futures = new ArrayList<>();
    final List<TestService> services = new ArrayList<>();

    for (int i = 0; i < 1_000; i++) {
      final TestService service = new TestService();
      service.future = new CompletableActorFuture<>();
      services.add(service);
      final ServiceBuilder<Void> builder =
          serviceContainer.createService(
              ServiceName.newServiceName("service" + i, Void.class), service);

      if (i < (1_000 - 1)) {
        builder.dependency(ServiceName.newServiceName("service" + (i + 1), Void.class));
      }

      futures.add(builder.install());
    }

    for (int i = 999; i >= 0; i--) {
      services.get(i).future.complete(null);
    }

    futures.forEach((f) -> f.join());
  }

  @Test
  public void testInstallComposite() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    final List<ActorFuture<Void>> futures = new ArrayList<>();
    final List<TestService> services = new ArrayList<>();

    final CompositeServiceBuilder installOperation =
        serviceContainer.createComposite(ServiceName.newServiceName("composite", Void.class));

    for (int i = 0; i < 1_000; i++) {
      final TestService service = new TestService();
      service.future = new CompletableActorFuture<>();
      services.add(service);
      final ServiceBuilder<Void> builder =
          installOperation.createService(
              ServiceName.newServiceName("service" + i, Void.class), service);

      if (i < (1_000 - 1)) {
        builder.dependency(ServiceName.newServiceName("service" + (i + 1), Void.class));
      }

      futures.add(builder.install());
    }

    final ActorFuture<Void> future = installOperation.install();

    for (int i = 999; i >= 0; i--) {
      services.get(i).future.complete(null);
    }

    future.join();
  }

  @Test
  public void testFailInstallComposite() {
    final ServiceContainer serviceContainer = serviceContainerRule.get();

    final List<ActorFuture<Void>> futures = new ArrayList<>();
    final List<TestService> services = new ArrayList<>();

    final CompositeServiceBuilder installOperation =
        serviceContainer.createComposite(ServiceName.newServiceName("composite", Void.class));

    for (int i = 0; i < 1_000; i++) {
      final TestService service = new TestService();
      service.future = new CompletableActorFuture<>();
      services.add(service);
      final ServiceBuilder<Void> builder =
          installOperation.createService(
              ServiceName.newServiceName("service" + i, Void.class), service);

      if (i < (1_000 - 1)) {
        builder.dependency(ServiceName.newServiceName("service" + (i + 1), Void.class));
      }

      futures.add(builder.install());
    }

    final ActorFuture<Void> future = installOperation.install();

    services.get(999).future.completeExceptionally(new RuntimeException());

    for (int i = 998; i >= 0; i--) {
      services.get(i).future.complete(null);
    }

    assertThatThrownBy(() -> future.join()).hasMessageContaining("Could not complete installation");
  }

  class TestService implements Service<Void> {
    CompletableActorFuture<Void> future;

    @Override
    public void start(ServiceStartContext startContext) {
      if (future != null) {
        startContext.async(future);
      }
    }

    @Override
    public void stop(ServiceStopContext stopContext) {}

    @Override
    public Void get() {
      return null;
    }
  }
}
