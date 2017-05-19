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
package org.camunda.tngp.broker.transport.controlmessage;

import java.util.Arrays;
import java.util.List;

import org.camunda.tngp.broker.event.handler.RemoveTopicSubscriptionHandler;
import org.camunda.tngp.broker.event.processor.TopicSubscriptionService;
import org.camunda.tngp.broker.system.threads.AgentRunnerServices;
import org.camunda.tngp.broker.task.TaskSubscriptionManager;
import org.camunda.tngp.broker.transport.clientapi.ErrorResponseWriter;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.agent.AgentRunnerService;

public class ControlMessageHandlerManagerService implements Service<ControlMessageHandlerManager>
{
    protected final Injector<Dispatcher> sendBufferInjector = new Injector<>();
    protected final Injector<Dispatcher> controlMessageBufferInjector = new Injector<>();
    protected final Injector<AgentRunnerServices> agentRunnerServicesInjector = new Injector<>();
    protected final Injector<TaskSubscriptionManager> taskSubscriptionManagerInjector = new Injector<>();
    protected final Injector<TopicSubscriptionService> topicSubscriptionServiceInjector = new Injector<>();

    protected final long controlMessageRequestTimeoutInMillis;

    protected ControlMessageHandlerManager service;

    public ControlMessageHandlerManagerService(long controlMessageRequestTimeoutInMillis)
    {
        this.controlMessageRequestTimeoutInMillis = controlMessageRequestTimeoutInMillis;
    }

    @Override
    public void start(ServiceStartContext context)
    {
        final Dispatcher controlMessageBuffer = controlMessageBufferInjector.getValue();

        final Dispatcher sendBuffer = sendBufferInjector.getValue();
        final ControlMessageResponseWriter controlMessageResponseWriter = new ControlMessageResponseWriter(sendBuffer);
        final ErrorResponseWriter errorResponseWriter = new ErrorResponseWriter(sendBuffer);

        final AgentRunnerService agentRunnerService = agentRunnerServicesInjector.getValue().conductorAgentRunnerService();

        final TaskSubscriptionManager taskSubscriptionManager = taskSubscriptionManagerInjector.getValue();
        final TopicSubscriptionService topicSubscriptionService = topicSubscriptionServiceInjector.getValue();

        final List<ControlMessageHandler> controlMessageHandlers = Arrays.asList(
            new AddTaskSubscriptionHandler(taskSubscriptionManager, controlMessageResponseWriter, errorResponseWriter),
            new IncreaseTaskSubscriptionCreditsHandler(taskSubscriptionManager, controlMessageResponseWriter, errorResponseWriter),
            new RemoveTaskSubscriptionHandler(taskSubscriptionManager, controlMessageResponseWriter, errorResponseWriter),
            new RemoveTopicSubscriptionHandler(topicSubscriptionService, controlMessageResponseWriter, errorResponseWriter)
        );

        service = new ControlMessageHandlerManager(controlMessageBuffer, errorResponseWriter, controlMessageRequestTimeoutInMillis, agentRunnerService, controlMessageHandlers);

        context.async(service.openAsync());
    }

    @Override
    public void stop(ServiceStopContext context)
    {
        context.async(service.closeAsync());
    }

    @Override
    public ControlMessageHandlerManager get()
    {
        return service;
    }

    public Injector<Dispatcher> getSendBufferInjector()
    {
        return sendBufferInjector;
    }

    public Injector<Dispatcher> getControlMessageBufferInjector()
    {
        return controlMessageBufferInjector;
    }

    public Injector<AgentRunnerServices> getAgentRunnerServicesInjector()
    {
        return agentRunnerServicesInjector;
    }

    public Injector<TaskSubscriptionManager> getTaskSubscriptionManagerInjector()
    {
        return taskSubscriptionManagerInjector;
    }

    public Injector<TopicSubscriptionService> getTopicSubscriptionServiceInjector()
    {
        return topicSubscriptionServiceInjector;
    }

}
