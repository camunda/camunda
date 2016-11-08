/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.broker.event;

import static org.camunda.tngp.broker.event.EventServiceNames.*;
import static org.camunda.tngp.broker.system.SystemServiceNames.*;
import static org.camunda.tngp.broker.transport.TransportServiceNames.*;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.*;

import org.camunda.tngp.broker.event.request.handler.PollEventsRequestHandler;
import org.camunda.tngp.broker.services.DeferredResponsePoolService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.transport.worker.AsyncRequestWorkerService;
import org.camunda.tngp.broker.transport.worker.BrokerRequestDispatcher;
import org.camunda.tngp.broker.transport.worker.BrokerRequestWorkerContextService;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.protocol.event.PollEventsDecoder;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;

public class EventComponent implements Component
{
    public static final String WORKER_NAME = "event-worker.0";

    @Override
    public void init(SystemContext context)
    {
        final ServiceContainer serviceContainer = context.getServiceContainer();
        final ConfigurationManager configurationManager = context.getConfigurationManager();

        final EventManagerService eventManagerService = new EventManagerService(configurationManager);
        serviceContainer.createService(EVENT_MANAGER_SERVICE, eventManagerService)
            .groupReference(EVENT_CONTEXT_SERVICE_GROUP_NAME, eventManagerService.getResourceContextsReference())
            .install();

        startWorkers(serviceContainer, eventManagerService);
    }

    protected void startWorkers(final ServiceContainer serviceContainer, final EventManagerService eventManagerService)
    {
        final BrokerRequestDispatcher<EventContext> eventRequestDispatcher = new BrokerRequestDispatcher<>(eventManagerService, PollEventsDecoder.SCHEMA_ID, new BrokerRequestHandler[] {
            new PollEventsRequestHandler()
        });

        final EventWorkerContext workerContext = new EventWorkerContext();
        workerContext.setEventManager(eventManagerService);
        workerContext.setRequestHandler(eventRequestDispatcher);

        final DeferredResponsePoolService responsePoolService = new DeferredResponsePoolService(1);
        final AsyncRequestWorkerService workerService = new AsyncRequestWorkerService();
        final BrokerRequestWorkerContextService workerContextService = new BrokerRequestWorkerContextService(workerContext);

        final ServiceName<DeferredResponsePool> responsePoolServiceName = workerResponsePoolServiceName(WORKER_NAME);
        serviceContainer.createService(responsePoolServiceName, responsePoolService)
            .dependency(TRANSPORT_SEND_BUFFER, responsePoolService.getSendBufferInector())
            .install();

        final ServiceName<AsyncRequestWorkerContext> workerContextServiceName = workerContextServiceName(WORKER_NAME);
        serviceContainer.createService(workerContextServiceName, workerContextService)
                .dependency(responsePoolServiceName, workerContextService.getResponsePoolInjector())
                .dependency(serverSocketBindingReceiveBufferName(CLIENT_API_SOCKET_BINDING_NAME), workerContextService.getRequestBufferInjector())
                .install();

        serviceContainer.createService(workerServiceName(WORKER_NAME), workerService)
            .dependency(workerContextServiceName, workerService.getWorkerContextInjector())
            .dependency(AGENT_RUNNER_SERVICE, workerService.getAgentRunnerInjector())
            .dependency(EVENT_MANAGER_SERVICE)
            .install();
    }

}
