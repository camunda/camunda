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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

@SuppressWarnings("unchecked")
public class InjectedDependencyTest {
  @Rule public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();

  ServiceContainer serviceContainer;

  ServiceName<Object> service1Name;
  ServiceName<Object> service2Name;

  Service<Object> mockService1;
  Service<Object> mockService2;

  Object mockService2Value;
  Object mockService1Value;

  @Before
  public void setup() {
    serviceContainer = new ServiceContainerImpl(actorSchedulerRule.get());
    serviceContainer.start();

    service1Name = ServiceName.newServiceName("service1", Object.class);
    service2Name = ServiceName.newServiceName("service2", Object.class);

    mockService1 = mock(Service.class);
    mockService2 = mock(Service.class);

    mockService1Value = new Object();
    when(mockService1.get()).thenReturn(mockService1Value);

    mockService2Value = new Object();
    when(mockService2.get()).thenReturn(mockService2Value);
  }

  @Test
  public void shouldInjectIfExistsBefore() {
    // given
    serviceContainer.createService(service2Name, mockService2).install();

    actorSchedulerRule.workUntilDone();

    // when
    final Injector<Object> injector = new Injector<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .install();

    actorSchedulerRule.workUntilDone();

    // then
    assertThat(injector.getValue()).isEqualTo(mockService2Value);
  }

  @Test
  public void shouldInjectIfStartedConcurrently() {
    // when
    final Injector<Object> injector = new Injector<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .install();
    serviceContainer.createService(service2Name, mockService2).install();

    actorSchedulerRule.workUntilDone();

    // then
    assertThat(injector.getValue()).isEqualTo(mockService2Value);
  }

  @Test
  public void shouldInjectBeforeCallingStart() {
    // when
    final Injector<Object> injector = mock(Injector.class);
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .install();
    serviceContainer.createService(service2Name, mockService2).install();

    actorSchedulerRule.workUntilDone();

    // then
    final InOrder inOrder = inOrder(mockService1, injector);
    inOrder.verify(injector).inject(mockService2Value);
    inOrder.verify(mockService1).start(any(ServiceStartContext.class));
  }

  @Test
  public void shouldInjectServiceTwice() {
    // given
    serviceContainer.createService(service2Name, mockService2).install();

    actorSchedulerRule.workUntilDone();

    // when
    final Injector<Object> injector = new Injector<>();
    final Injector<Object> anotherInjector = new Injector<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .dependency(service2Name, anotherInjector)
        .install();

    actorSchedulerRule.workUntilDone();

    // then
    assertThat(injector.getValue()).isEqualTo(mockService2Value);
    assertThat(anotherInjector.getValue()).isEqualTo(mockService2Value);
  }

  @Test
  public void shouldUninject() {
    // given
    final Injector<Object> injector = new Injector<>();
    serviceContainer.createService(service2Name, mockService2).install();
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .install();

    actorSchedulerRule.workUntilDone();

    // when
    serviceContainer.removeService(service1Name);

    actorSchedulerRule.workUntilDone();

    // then
    assertThat(injector.getValue()).isNull();
  }

  @Test
  public void shouldUninjectAfterStop() {
    // given
    final Injector<Object> injector = mock(Injector.class);
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .install();
    serviceContainer.createService(service2Name, mockService2).install();

    actorSchedulerRule.workUntilDone();

    // when
    serviceContainer.removeService(service1Name);

    actorSchedulerRule.workUntilDone();

    // then
    final InOrder inOrder = inOrder(mockService1, injector);
    inOrder.verify(mockService1).stop(any(ServiceStopContext.class));
    inOrder.verify(injector).uninject();
  }

  @Test
  public void shouldUninjectServiceTwice() {
    // given
    final Injector<Object> injector = new Injector<>();
    final Injector<Object> anotherInjector = new Injector<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .dependency(service2Name, anotherInjector)
        .install();
    serviceContainer.createService(service2Name, mockService2).install();

    actorSchedulerRule.workUntilDone();

    // when
    serviceContainer.removeService(service1Name);

    actorSchedulerRule.workUntilDone();

    // then
    assertThat(injector.getValue()).isNull();
    assertThat(anotherInjector.getValue()).isNull();
  }

  @Test
  public void shouldHaveService() {
    // given
    final Injector<Object> injector = new Injector<>();
    final Injector<Object> anotherInjector = new Injector<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .dependency(service2Name, injector)
        .dependency(service2Name, anotherInjector)
        .install();
    serviceContainer.createService(service2Name, mockService2).install();

    actorSchedulerRule.workUntilDone();

    // when + then
    assertThat(serviceContainer.hasService(service1Name)).isTrue();
    assertThat(serviceContainer.hasService(service2Name)).isTrue();
  }

  @Test
  public void shouldNotHaveService() {
    // when + then
    assertThat(serviceContainer.hasService(service1Name)).isFalse();
    assertThat(serviceContainer.hasService(service2Name)).isFalse();
  }
}
