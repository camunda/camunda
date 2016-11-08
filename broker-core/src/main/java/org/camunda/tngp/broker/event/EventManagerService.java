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
import static org.camunda.tngp.broker.log.LogServiceNames.*;

import org.camunda.tngp.broker.system.AbstractResourceContextProvider;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class EventManagerService extends AbstractResourceContextProvider<EventContext> implements Service<EventManager>, EventManager
{
    protected final ConfigurationManager configurationManager;

    public EventManagerService(ConfigurationManager configurationManager)
    {
        super(EventContext.class);

        this.configurationManager = configurationManager;
    }

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final EventContextService eventContextService = new EventContextService();
        serviceContext.createService(EVENT_CONTEXT_SERVICE, eventContextService)
            .group(EVENT_CONTEXT_SERVICE_GROUP_NAME)
            .dependency(LOG_MANAGER_SERVICE, eventContextService.getLogManagerInjector())
            .install();
    }

    @Override
    public void stop(ServiceStopContext serviceStopContext)
    {
        // nothing to do
    }

    @Override
    public EventManager get()
    {
        return this;
    }

}
