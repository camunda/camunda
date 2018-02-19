/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.services;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.DispatcherBuilder;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.ZbActorScheduler;

public class DispatcherService implements Service<Dispatcher>
{
    protected final Injector<ZbActorScheduler> actorSchedulerInjector = new Injector<>();
    protected final Injector<Counters> countersInjector = new Injector<>();

    protected DispatcherBuilder dispatcherBuilder;
    protected Dispatcher dispatcher;

    public DispatcherService(DispatcherBuilder builder)
    {
        this.dispatcherBuilder = builder;
    }

    @Override
    public void start(ServiceStartContext ctx)
    {
        final Counters counters = countersInjector.getValue();

        dispatcher = dispatcherBuilder
                .name(ctx.getName())
                .countersManager(counters.getCountersManager())
                .countersBuffer(counters.getCountersBuffer())
                .actorScheduler(actorSchedulerInjector.getValue())
                .build();
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        ctx.async(dispatcher.closeAsync());
    }

    @Override
    public Dispatcher get()
    {
        return dispatcher;
    }

    public Injector<ZbActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public Injector<Counters> getCountersManagerInjector()
    {
        return countersInjector;
    }
}
