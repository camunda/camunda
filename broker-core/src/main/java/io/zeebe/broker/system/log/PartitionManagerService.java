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
package io.zeebe.broker.system.log;

import io.zeebe.broker.clustering.management.memberList.MemberListService;
import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.clustering.management.PartitionManagerImpl;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ClientTransport;

public class PartitionManagerService implements Service<PartitionManager>
{
    private final Injector<MemberListService> memberListServiceInjector = new Injector<>();
    private final Injector<ClientTransport> managementClientInjector = new Injector<>();

    private PartitionManager service;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ClientTransport managementClient = managementClientInjector.getValue();

        service = new PartitionManagerImpl(memberListServiceInjector.getValue(), managementClient);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
    }

    @Override
    public PartitionManager get()
    {
        return service;
    }

    public Injector<MemberListService> getMemberListServiceInjector()
    {
        return memberListServiceInjector;
    }

    public Injector<ClientTransport> getManagementClientInjector()
    {
        return managementClientInjector;
    }

}
