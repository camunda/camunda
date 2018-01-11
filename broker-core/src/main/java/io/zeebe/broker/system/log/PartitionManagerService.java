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

import java.time.Duration;

import io.zeebe.broker.clustering.management.PartitionManager;
import io.zeebe.broker.clustering.management.PartitionManagerImpl;
import io.zeebe.broker.clustering.management.memberList.MemberListService;
import io.zeebe.broker.system.executor.ScheduledCommand;
import io.zeebe.broker.system.executor.ScheduledExecutor;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.ClientTransport;

public class PartitionManagerService implements Service<PartitionManager>
{
    private static final Duration ASYNC_INTERVAL = Duration.ofMillis(100);

    private final Injector<MemberListService> memberListServiceInjector = new Injector<>();
    private final Injector<ClientTransport> managementClientInjector = new Injector<>();
    private final Injector<ScheduledExecutor> executorService = new Injector<>();

    private PartitionManager service;

    private ScheduledCommand command;

    private ScheduledExecutor executor;

    private CloseResolvedRequestsCommand closeRequestsCmd;

    @Override
    public void start(ServiceStartContext startContext)
    {
        final ClientTransport managementClient = managementClientInjector.getValue();
        executor = executorService.getValue();

        closeRequestsCmd = new CloseResolvedRequestsCommand();
        command = executor.scheduleAtFixedRate(closeRequestsCmd, ASYNC_INTERVAL);

        service = new PartitionManagerImpl(memberListServiceInjector.getValue(), managementClient, closeRequestsCmd);

    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        if (command != null)
        {
            command.cancel();
            closeRequestsCmd.close();
        }

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

    public Injector<ScheduledExecutor> getExecutorServiceInjector()
    {
        return executorService;
    }

}
