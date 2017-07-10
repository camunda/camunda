/**
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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;

import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

@SuppressWarnings("unchecked")
public class ServiceStartContextTest
{
    private ControlledServiceContainer serviceContainer;

    private ServiceName<Object> service1;
    private ServiceName<Object> service2;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup()
    {
        serviceContainer = new ControlledServiceContainer();
        serviceContainer.start();

        service1 = ServiceName.newServiceName("service1", Object.class);
        service2 = ServiceName.newServiceName("service2", Object.class);
    }

    @Test
    public void shouldCreateService()
    {
        final MockService mockService1 = new MockService();
        final Service<Object> mockService2 = mock(Service.class);

        final CompletableFuture<Void> service1Future = serviceContainer.createService(service1, mockService1).install();

        serviceContainer.doWorkUntilDone();
        assertCompleted(service1Future);

        final CompletableFuture<Void> service2Future = mockService1.startContext.createService(service2, mockService2).install();

        serviceContainer.doWorkUntilDone();
        assertCompleted(service2Future);

        verify(mockService2, times(1)).start(any(ServiceStartContext.class));
    }

    @Test
    public void shouldRemoveService()
    {
        final MockService mockService1 = new MockService();
        final Service<Object> mockService2 = mock(Service.class);

        serviceContainer.createService(service1, mockService1).install();
        serviceContainer.doWorkUntilDone();

        mockService1.startContext.createService(service2, mockService2).install();
        serviceContainer.doWorkUntilDone();

        final CompletableFuture<Void> service2Future = mockService1.startContext.removeService(service2);

        serviceContainer.doWorkUntilDone();
        assertCompleted(service2Future);

        verify(mockService2, times(1)).stop(any(ServiceStopContext.class));
    }

    @Test
    public void shouldNotRemoveServiceIfNotCreatedFromContext()
    {
        final MockService mockService1 = new MockService();
        final Service<Object> mockService2 = mock(Service.class);

        serviceContainer.createService(service1, mockService1).install();
        serviceContainer.doWorkUntilDone();

        serviceContainer.createService(service2, mockService2).install();
        serviceContainer.doWorkUntilDone();

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot remove service 'service2' from context 'service1'. The context is not a dependency of the service.");

        mockService1.startContext.removeService(service2);
    }

    @Test
    public void shouldNotRemoveServiceIfNotExist()
    {
        final MockService mockService1 = new MockService();

        serviceContainer.createService(service1, mockService1).install();
        serviceContainer.doWorkUntilDone();

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Cannot remove service 'service2' from context 'service1'. Service not found.");

        mockService1.startContext.removeService(service2);
    }

    protected void assertCompleted(CompletableFuture<Void> serviceFuture)
    {
        assertThat(serviceFuture).isCompleted();
    }

    class MockService implements Service<Object>
    {
        ServiceStartContext startContext;

        @Override
        public void start(ServiceStartContext startContext)
        {
            this.startContext = startContext;
        }

        @Override
        public void stop(ServiceStopContext stopContext)
        {
        }

        @Override
        public Object get()
        {
            return "mock service";
        }

    }


}
