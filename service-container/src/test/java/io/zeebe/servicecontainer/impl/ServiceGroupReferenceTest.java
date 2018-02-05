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

import static io.zeebe.servicecontainer.ServiceGroupReference.collection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.servicecontainer.*;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.*;
import org.mockito.InOrder;

@SuppressWarnings("unchecked")
public class ServiceGroupReferenceTest
{
    @Rule
    public final ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();

    private ServiceContainerImpl serviceContainer;

    ServiceName<Object> service1Name;
    ServiceName<Object> service2Name;

    ServiceName<Object> group1Name;
    ServiceName<Object> group2Name;

    Service<Object> mockService1;
    Service<Object> mockService2;

    Object mockService2Value;
    Object mockService1Value;

    @Before
    public void setup()
    {
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
    public void shouldAddNoReferences()
    {
        // given no service registered with group name "group1"

        // when
        final List<Object> injectedServices = new ArrayList<>();
        serviceContainer.createService(service1Name, mockService1)
                        .groupReference(group1Name, collection(injectedServices))
                        .install();
        actorSchedulerRule.workUntilDone();

        // then
        assertThat(injectedServices).isEmpty();
    }

    @Test
    public void shouldNotAddReferenceOfDifferentGroup()
    {
        // given
        serviceContainer.createService(service2Name, mockService2)
                        .group(group2Name)
                        .install();
        actorSchedulerRule.workUntilDone();

        // when
        final List<Object> injectedServices = new ArrayList<>();
        serviceContainer.createService(service1Name, mockService1)
                        .groupReference(group1Name, collection(injectedServices))
                        .install();
        actorSchedulerRule.workUntilDone();

        // then
        assertThat(injectedServices).isEmpty();
    }

    @Test
    public void shouldAddReferenceIfExistsBefore()
    {
        // given
        serviceContainer.createService(service2Name, mockService2)
                        .group(group1Name)
                        .install();
        actorSchedulerRule.workUntilDone();

        // when
        final List<Object> injectedServices = new ArrayList<>();
        serviceContainer.createService(service1Name, mockService1)
                        .groupReference(group1Name, collection(injectedServices))
                        .install();
        actorSchedulerRule.workUntilDone();

        // then
        assertThat(injectedServices).contains(mockService2Value);
    }

    @Test
    public void shouldAddReferenceIfStartedAfter()
    {
        // given
        final List<Object> injectedServices = new ArrayList<>();
        serviceContainer.createService(service1Name, mockService1)
                        .groupReference(group1Name, collection(injectedServices))
                        .install();
        actorSchedulerRule.workUntilDone();

        // when
        serviceContainer.createService(service2Name, mockService2)
                        .group(group1Name)
                        .install();
        actorSchedulerRule.workUntilDone();

        // then
        assertThat(injectedServices).contains(mockService2Value);
    }

    @Test
    public void shouldAddReferenceIfStartedConcurrently()
    {
        // given
        final List<Object> injectedServices = new ArrayList<>();
        serviceContainer.createService(service1Name, mockService1)
                        .groupReference(group1Name, collection(injectedServices))
                        .install();
        serviceContainer.createService(service2Name, mockService2)
                        .group(group1Name)
                        .install();

        // when
        actorSchedulerRule.workUntilDone();

        // then
        assertThat(injectedServices).contains(mockService2Value);
    }

    @Test
    public void shouldAddReferenceAfterCallingStart()
    {
        // given
        final List<Object> injectedServices = mock(List.class);
        serviceContainer.createService(service1Name, mockService1)
                        .groupReference(group1Name, collection(injectedServices))
                        .install();
        actorSchedulerRule.workUntilDone();

        // when
        serviceContainer.createService(service2Name, mockService2)
                        .group(group1Name)
                        .install();
        actorSchedulerRule.workUntilDone();

        // then
        final InOrder inOrder = inOrder(mockService1, injectedServices);
        inOrder.verify(mockService1)
               .start(any(ServiceStartContext.class));
        inOrder.verify(injectedServices)
               .add(mockService2Value);
    }

    @Test
    public void shouldPreInjectExistingReference()
    {
        // given
        serviceContainer.createService(service2Name, mockService2)
                        .group(group1Name)
                        .install();
        actorSchedulerRule.workUntilDone();

        // when
        final List<Object> injectedServices = mock(List.class);
        serviceContainer.createService(service1Name, mockService1)
                        .groupReference(group1Name, collection(injectedServices))
                        .install();
        actorSchedulerRule.workUntilDone();

        // then
        final InOrder inOrder = inOrder(mockService1, injectedServices);
        inOrder.verify(mockService1)
               .start(any(ServiceStartContext.class));
        inOrder.verify(injectedServices)
               .add(mockService2Value);
    }

    @Test
    public void shouldRemoveReferenceIfStoppedBefore()
    {
        // given
        final List<Object> injectedServices = mock(List.class);
        serviceContainer.createService(service1Name, mockService1)
                        .groupReference(group1Name, collection(injectedServices))
                        .install();
        serviceContainer.createService(service2Name, mockService2)
                        .group(group1Name)
                        .install();
        actorSchedulerRule.workUntilDone();

        // when
        serviceContainer.removeService(service2Name);
        actorSchedulerRule.workUntilDone();

        // then
        final InOrder inOrder = inOrder(injectedServices, mockService2);
        inOrder.verify(mockService2)
               .stop(any(ServiceStopContext.class));
        inOrder.verify(injectedServices)
               .remove(mockService2Value);
    }

    @Test
    public void shouldRemoveReference()
    {
        // given
        final List<Object> injectedServices = mock(List.class);
        serviceContainer.createService(service1Name, mockService1)
                        .groupReference(group1Name, collection(injectedServices))
                        .install();
        serviceContainer.createService(service2Name, mockService2)
                        .group(group1Name)
                        .install();
        actorSchedulerRule.workUntilDone();

        // when
        serviceContainer.removeService(service1Name);
        actorSchedulerRule.workUntilDone();

        // then
        final InOrder inOrder = inOrder(injectedServices, mockService1);
        inOrder.verify(mockService1)
               .stop(any(ServiceStopContext.class));
        inOrder.verify(injectedServices)
               .remove(mockService2Value);
    }

    @Test
    public void shouldRemoveReferenceIfStoppedConcurrently()
    {
        // given
        final List<Object> injectedServices = mock(List.class);
        serviceContainer.createService(service1Name, mockService1)
                        .groupReference(group1Name, collection(injectedServices))
                        .install();
        serviceContainer.createService(service2Name, mockService2)
                        .group(group1Name)
                        .install();
        actorSchedulerRule.workUntilDone();

        // when
        serviceContainer.removeService(service2Name);
        serviceContainer.removeService(service1Name);
        actorSchedulerRule.workUntilDone();

        // then
        // in this case, there is no guarantee on the ordering
        verify(injectedServices).remove(mockService2Value);
    }
}
