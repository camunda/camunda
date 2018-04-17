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

import io.zeebe.servicecontainer.*;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.InOrder;

import java.util.concurrent.CompletableFuture;

import static io.zeebe.servicecontainer.impl.ActorFutureAssertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class CompositeInstallOperationTest
{
    public ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();
    public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(serviceContainerRule);

    ServiceName<Void> compositeName = ServiceName.newServiceName("composite", Void.class);
    ServiceName<Object> service1Name = ServiceName.newServiceName("service1", Object.class);
    ServiceName<Object> service2Name = ServiceName.newServiceName("service2", Object.class);

    private Service<Object> mockService1;
    private Service<Object> mockService2;

    @Before
    public void setup()
    {
        mockService1 = mock(Service.class);
        mockService2 = mock(Service.class);
    }

    @Test
    public void testEmptyOperation()
    {
        final ServiceContainer container = serviceContainerRule.get();

        // when
        final ActorFuture<Void> future = container.createComposite(compositeName).install();
        actorSchedulerRule.workUntilDone();

        // then
        assertCompleted(future);
    }

    @Test
    public void testCompositeInstall()
    {
        final ServiceContainer container = serviceContainerRule.get();

        // when
        final CompositeServiceBuilder composite = container.createComposite(compositeName);

        final ActorFuture<Object> service1Future = composite.createService(service1Name, mockService1).install();
        final ActorFuture<Object> service2Future = composite.createService(service1Name, mockService2).install();

        final ActorFuture<Void> compositeFuture = composite.install();

        actorSchedulerRule.workUntilDone();

        // then
        assertCompleted(service1Future);
        assertCompleted(service2Future);
        assertCompleted(compositeFuture);
    }

    @Test
    public void testCompositeInstallAsync()
    {
        final ServiceContainer container = serviceContainerRule.get();

        final AsyncStartService asyncService2 = new AsyncStartService();
        asyncService2.future = new CompletableFuture<>();

        // when
        final CompositeServiceBuilder composite = container.createComposite(compositeName);

        final ActorFuture<Object> service1Future = composite.createService(service1Name, mockService1).install();
        final ActorFuture<Object> service2Future = composite.createService(service1Name, asyncService2).install();

        final ActorFuture<Void> compositeFuture = composite.install();

        actorSchedulerRule.workUntilDone();

        // then
        assertCompleted(service1Future);
        assertNotCompleted(service2Future);
        assertNotCompleted(compositeFuture);

        // when
        asyncService2.future.complete(null);
        actorSchedulerRule.awaitBlockingTasksCompleted(1);
        actorSchedulerRule.workUntilDone();

        assertCompleted(service1Future);
        assertCompleted(service2Future);
        assertCompleted(compositeFuture);
    }

    @Test
    public void testCompositeInstallAsyncFails()
    {
        final ServiceContainer container = serviceContainerRule.get();

        final AsyncStartService asyncService2 = new AsyncStartService();
        asyncService2.future = new CompletableFuture<>();

        // when
        final CompositeServiceBuilder composite = container.createComposite(compositeName);

        final ActorFuture<Object> service1Future = composite.createService(service1Name, mockService1).install();
        final ActorFuture<Object> service2Future = composite.createService(service1Name, asyncService2).install();

        final ActorFuture<Void> compositeFuture = composite.install();

        actorSchedulerRule.workUntilDone();

        // then
        assertCompleted(service1Future);
        assertNotCompleted(service2Future);
        assertNotCompleted(compositeFuture);

        assertThat(container.hasService(service1Name)).isTrue();

        // when
        asyncService2.future.completeExceptionally(new RuntimeException());
        actorSchedulerRule.awaitBlockingTasksCompleted(1);
        actorSchedulerRule.workUntilDone();

        assertCompleted(service1Future); // future is still completed
        assertFailed(service2Future);
        assertFailed(compositeFuture);

        // both services have been removed
        assertThat(container.hasService(service1Name)).isFalse();
        assertThat(container.hasService(service2Name)).isFalse();

        // stop has been called in service 1
        final InOrder inOrder = inOrder(mockService1);
        inOrder.verify(mockService1, times(1)).start(any());
        inOrder.verify(mockService1, times(1)).stop(any());
    }

    static class AsyncStartService implements Service<Object>
    {
        CompletableFuture<Void> future;
        Object value = new Object();
        Runnable action;
        volatile boolean wasStopped = false;

        @Override
        public void start(ServiceStartContext startContext)
        {
            if (action != null)
            {
                startContext.run(action);
            }
            else if (future != null)
            {
                startContext.async(future);
            }
        }

        @Override
        public void stop(ServiceStopContext stopContext)
        {
            wasStopped = true;
        }

        @Override
        public Object get()
        {
            return value;
        }
    }

}
