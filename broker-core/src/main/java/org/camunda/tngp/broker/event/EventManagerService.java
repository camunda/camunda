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

import static org.camunda.tngp.broker.event.EventServiceNames.EVENT_CONTEXT_SERVICE;
import static org.camunda.tngp.broker.log.LogServiceNames.LOG_MANAGER_SERVICE;

import org.camunda.tngp.broker.system.AbstractResourceContextProvider;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class EventManagerService extends AbstractResourceContextProvider<EventContext> implements Service<EventManager>, EventManager
{
    protected final ConfigurationManager configurationManager;

    protected ServiceContext serviceContext;

    public EventManagerService(ConfigurationManager configurationManager)
    {
        super(EventContext.class);

        this.configurationManager = configurationManager;
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        this.serviceContext = serviceContext;

        final EventContextService eventContextService = new EventContextService();
        serviceContext.createService(EVENT_CONTEXT_SERVICE, eventContextService)
            .dependency(LOG_MANAGER_SERVICE, eventContextService.getLogManagerInjector())
            .listener(this)
            .install();
    }

    @Override
    public void stop()
    {
        // nothing to do
    }

    @Override
    public EventManager get()
    {
        return this;
    }

}
