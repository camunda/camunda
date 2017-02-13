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
package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.logstreams.log.LogStream;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class TaskSubscriptionManagerService implements Service<TaskSubscriptionManager>
{
    protected final Injector<AgentRunnerServices> agentRunnerServicesInjector = new Injector<>();

    protected ServiceStartContext serviceContext;

    protected TaskSubscriptionManager service;
    protected AgentRunnerService agentRunnerService;

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
        .onAdd((name, stream) -> service.addStream(stream, name))
        .onRemove((name, stream) -> service.removeStream(stream))
        .build();

    @Override
    public void start(ServiceStartContext startContext)
    {
        final AgentRunnerServices agentRunnerServices = agentRunnerServicesInjector.getValue();
        agentRunnerService = agentRunnerServices.conductorAgentRunnerService();

        service = new TaskSubscriptionManager(startContext);

        agentRunnerService.run(service);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        agentRunnerService.remove(service);
    }

    @Override
    public TaskSubscriptionManager get()
    {
        return service;
    }

    public Injector<AgentRunnerServices> getAgentRunnerServicesInjector()
    {
        return agentRunnerServicesInjector;
    }

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }

}
