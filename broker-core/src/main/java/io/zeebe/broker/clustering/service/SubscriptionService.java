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
package io.zeebe.broker.clustering.service;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

// TODO: still needed?
public class SubscriptionService implements Service<Subscription>
{
    private final Injector<Dispatcher> receiveBufferInjector = new Injector<>();

    private Subscription subscription;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final Dispatcher receiveBuffer = receiveBufferInjector.getValue();
        subscription = receiveBuffer.openSubscription(startContext.getName());
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        stopContext.async(subscription.closeAsnyc());
    }

    @Override
    public Subscription get()
    {
        return subscription;
    }

    public Injector<Dispatcher> getReceiveBufferInjector()
    {
        return receiveBufferInjector;
    }

}
