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
package io.zeebe.broker.task;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.servicecontainer.*;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;

public class TaskSubscriptionManagerService implements Service<TaskSubscriptionManager>
{
    protected final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();

    protected ServiceStartContext serviceContext;

    protected TaskSubscriptionManager service;
    protected ActorReference actorRef;

    protected final ServiceGroupReference<LogStream> logStreamsGroupReference = ServiceGroupReference.<LogStream>create()
        .onAdd((name, stream) -> service.addStream(stream, name))
        .onRemove((name, stream) -> service.removeStream(stream))
        .build();

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();

        service = new TaskSubscriptionManager(startContext);

        actorRef = actorScheduler.schedule(service);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        actorRef.close();
    }

    @Override
    public TaskSubscriptionManager get()
    {
        return service;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public ServiceGroupReference<LogStream> getLogStreamsGroupReference()
    {
        return logStreamsGroupReference;
    }

}
