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
package org.camunda.tngp.broker.system.executor;

import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.util.actor.ActorScheduler;

public class ScheduledExecutorService implements Service<ScheduledExecutor>
{
    protected final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();

    protected ScheduledExecutorImpl executor;

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.run(() ->
        {
            final ActorScheduler agentRunnerService = actorSchedulerInjector.getValue();

            executor = new ScheduledExecutorImpl(agentRunnerService);

            executor.start();
        });
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        ctx.async(executor.stopAsync());
    }

    @Override
    public ScheduledExecutor get()
    {
        return executor;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

}
