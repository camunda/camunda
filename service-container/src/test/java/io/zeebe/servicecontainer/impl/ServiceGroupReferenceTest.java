/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer.impl;

import static io.zeebe.servicecontainer.ServiceGroupReference.collection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.ActorTask;
import io.zeebe.util.sched.ActorThread;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

@SuppressWarnings("unchecked")
public class ServiceGroupReferenceTest {
  @Rule
  public final ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();

  ServiceName<Object> service1Name;
  ServiceName<Object> service2Name;
  ServiceName<Object> group1Name;
  ServiceName<Object> group2Name;
  Service<Object> mockService1;
  Service<Object> mockService2;
  Object mockService2Value;
  Object mockService1Value;
  private ServiceContainerImpl serviceContainer;

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

    group1Name = ServiceName.newServiceName("group1", Object.class);
    group2Name = ServiceName.newServiceName("group2", Object.class);
  }

  @Test
  public void shouldAddNoReferences() {
    // given no service registered with group name "group1"

    // when
    final List<Object> injectedServices = new ArrayList<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .groupReference(group1Name, collection(injectedServices))
        .install();
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(injectedServices).isEmpty();
  }

  @Test
  public void shouldNotAddReferenceOfDifferentGroup() {
    // given
    serviceContainer.createService(service2Name, mockService2).group(group2Name).install();
    actorSchedulerRule.workUntilDone();

    // when
    final List<Object> injectedServices = new ArrayList<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .groupReference(group1Name, collection(injectedServices))
        .install();
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(injectedServices).isEmpty();
  }

  @Test
  public void shouldAddReferenceIfExistsBefore() {
    // given
    serviceContainer.createService(service2Name, mockService2).group(group1Name).install();
    actorSchedulerRule.workUntilDone();

    // when
    final List<Object> injectedServices = new ArrayList<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .groupReference(group1Name, collection(injectedServices))
        .install();
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(injectedServices).contains(mockService2Value);
  }

  @Test
  public void shouldAddReferenceIfStartedAfter() {
    // given
    final List<Object> injectedServices = new ArrayList<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .groupReference(group1Name, collection(injectedServices))
        .install();
    actorSchedulerRule.workUntilDone();

    // when
    serviceContainer.createService(service2Name, mockService2).group(group1Name).install();
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(injectedServices).contains(mockService2Value);
  }

  @Test
  public void shouldAddReferenceIfStartedConcurrently() {
    // given
    final List<Object> injectedServices = new ArrayList<>();
    serviceContainer
        .createService(service1Name, mockService1)
        .groupReference(group1Name, collection(injectedServices))
        .install();
    serviceContainer.createService(service2Name, mockService2).group(group1Name).install();

    // when
    actorSchedulerRule.workUntilDone();

    // then
    assertThat(injectedServices).contains(mockService2Value);
  }

  @Test
  public void shouldAddReferenceAfterCallingStart() {
    // given
    final List<Object> injectedServices = mock(List.class);
    serviceContainer
        .createService(service1Name, mockService1)
        .groupReference(group1Name, collection(injectedServices))
        .install();
    actorSchedulerRule.workUntilDone();

    // when
    serviceContainer.createService(service2Name, mockService2).group(group1Name).install();
    actorSchedulerRule.workUntilDone();

    // then
    final InOrder inOrder = inOrder(mockService1, injectedServices);
    inOrder.verify(mockService1).start(any(ServiceStartContext.class));
    inOrder.verify(injectedServices).add(mockService2Value);
  }

  @Test
  public void shouldPreInjectExistingReference() {
    // given
    serviceContainer.createService(service2Name, mockService2).group(group1Name).install();
    actorSchedulerRule.workUntilDone();

    // when
    final List<Object> injectedServices = mock(List.class);
    serviceContainer
        .createService(service1Name, mockService1)
        .groupReference(group1Name, collection(injectedServices))
        .install();
    actorSchedulerRule.workUntilDone();

    // then
    final InOrder inOrder = inOrder(mockService1, injectedServices);
    inOrder.verify(mockService1).start(any(ServiceStartContext.class));
    inOrder.verify(injectedServices).add(mockService2Value);
  }

  @Test
  public void shouldRemoveReferenceIfStoppedBefore() {
    // given
    final List<Object> injectedServices = mock(List.class);
    serviceContainer
        .createService(service1Name, mockService1)
        .groupReference(group1Name, collection(injectedServices))
        .install();
    serviceContainer.createService(service2Name, mockService2).group(group1Name).install();
    actorSchedulerRule.workUntilDone();

    // when
    serviceContainer.removeService(service2Name);
    actorSchedulerRule.workUntilDone();

    // then
    final InOrder inOrder = inOrder(injectedServices, mockService2);
    inOrder.verify(injectedServices).remove(mockService2Value);
    inOrder.verify(mockService2).stop(any(ServiceStopContext.class));
  }

  @Test
  public void shouldRemoveReference() {
    // given
    final List<Object> injectedServices = mock(List.class);
    serviceContainer
        .createService(service1Name, mockService1)
        .groupReference(group1Name, collection(injectedServices))
        .install();
    serviceContainer.createService(service2Name, mockService2).group(group1Name).install();
    actorSchedulerRule.workUntilDone();

    // when
    serviceContainer.removeService(service1Name);
    actorSchedulerRule.workUntilDone();

    // then
    final InOrder inOrder = inOrder(injectedServices, mockService1);
    inOrder.verify(injectedServices).remove(mockService2Value);
    inOrder.verify(mockService1).stop(any(ServiceStopContext.class));
  }

  @Test
  public void shouldRemoveReferenceIfStoppedConcurrently() {
    // given
    final List<Object> injectedServices = mock(List.class);
    serviceContainer
        .createService(service1Name, mockService1)
        .groupReference(group1Name, collection(injectedServices))
        .install();
    serviceContainer.createService(service2Name, mockService2).group(group1Name).install();
    actorSchedulerRule.workUntilDone();

    // when
    serviceContainer.removeService(service2Name);
    serviceContainer.removeService(service1Name);
    actorSchedulerRule.workUntilDone();

    // then
    // in this case, there is no guarantee on the ordering
    verify(injectedServices).remove(mockService2Value);
  }

  @Test
  public void shouldInvokeAllServiceCallbacksInSameActorContext() {
    // given
    final ActorCapturingService service = new ActorCapturingService();
    serviceContainer
        .createService(service1Name, service)
        .groupReference(group1Name, service.logStreamsGroupReference)
        .install();
    serviceContainer.createService(service2Name, mockService2).group(group1Name).install();

    actorSchedulerRule.workUntilDone();

    serviceContainer.removeService(service2Name);
    serviceContainer.removeService(service1Name);
    actorSchedulerRule.workUntilDone();

    // when
    final Map<String, ActorTask> context = service.actorContext;

    // then
    assertThat(context).containsKeys("start", "stop", "reference-add", "reference-remove");

    final ActorTask actorTask = context.get("start");
    assertThat(context.values()).containsOnly(actorTask);
  }

  class ActorCapturingService implements Service<Object> {
    Map<String, ActorTask> actorContext = new HashMap<>();

    ServiceGroupReference<Object> logStreamsGroupReference =
        ServiceGroupReference.<Object>create()
            .onAdd(this::onReferenceAdd)
            .onRemove(this::onReferenceRemove)
            .build();

    @Override
    public void start(ServiceStartContext startContext) {
      recordCurrentTask("start");
    }

    @Override
    public void stop(ServiceStopContext stopContext) {
      recordCurrentTask("stop");
    }

    @Override
    public Void get() {
      return null;
    }

    public void onReferenceAdd(ServiceName<Object> name, Object o) {
      recordCurrentTask("reference-add");
    }

    public void onReferenceRemove(ServiceName<Object> name, Object o) {
      recordCurrentTask("reference-remove");
    }

    protected void recordCurrentTask(String identifier) {
      final ActorTask currentTask = ActorThread.current().getCurrentTask();
      actorContext.put(identifier, currentTask);
    }
  }
}
