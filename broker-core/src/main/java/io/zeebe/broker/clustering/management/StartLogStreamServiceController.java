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
package io.zeebe.broker.clustering.management;

import static io.zeebe.broker.clustering.ClusterServiceNames.CLUSTER_MANAGER_SERVICE;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.logStreamServiceName;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;

import java.util.concurrent.CompletableFuture;

import io.zeebe.broker.logstreams.LogStreamService;
import io.zeebe.broker.logstreams.LogStreamServiceNames;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.Protocol;
import io.zeebe.raft.Raft;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.state.SimpleStateMachineContext;
import io.zeebe.util.state.State;
import io.zeebe.util.state.StateMachine;

public class StartLogStreamServiceController
{

    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_FAILED = 1;
    private static final int TRANSITION_OPEN = 2;
    private static final int TRANSITION_CLOSE = 3;

    private final StateMachine<Context> stateMachine;

    public StartLogStreamServiceController(final ServiceName<Raft> raftServiceName, final Raft raft, final ServiceContainer serviceContainer)
    {
        final State<Context> startLogStreamService = new StartLogStreamServiceState();
        final State<Context> awaitStartLogStreamService = new AwaitServiceFutureState();
        final State<Context> open = new OpenState();
        final State<Context> stopLogStreamService = new StopLogStreamServiceState();
        final State<Context> awaitStopLogStreamService = new AwaitServiceFutureState();
        final State<Context> closed = new ClosedState();

        stateMachine = StateMachine.<Context>builder(s -> new Context(s, raftServiceName, raft, serviceContainer))
            .initialState(closed)
            .from(closed).take(TRANSITION_OPEN).to(startLogStreamService)
            .from(closed).take(TRANSITION_CLOSE).to(closed)

            .from(startLogStreamService).take(TRANSITION_DEFAULT).to(awaitStartLogStreamService)

            .from(awaitStartLogStreamService).take(TRANSITION_DEFAULT).to(open)
            .from(awaitStartLogStreamService).take(TRANSITION_FAILED).to(startLogStreamService)

            .from(open).take(TRANSITION_CLOSE).to(stopLogStreamService)
            .from(open).take(TRANSITION_OPEN).to(open)

            .from(stopLogStreamService).take(TRANSITION_DEFAULT).to(awaitStopLogStreamService)


            .from(awaitStopLogStreamService).take(TRANSITION_DEFAULT).to(closed)
            .from(awaitStopLogStreamService).take(TRANSITION_FAILED).to(stopLogStreamService)

            .build();
    }

    public int doWork()
    {
        return stateMachine.doWork();
    }

    public Raft getRaft()
    {
        return stateMachine.getContext().getRaft();
    }

    public ServiceName<LogStream> getServiceName()
    {
        return stateMachine.getContext().getServiceName();
    }

    static class StartLogStreamServiceState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            final ServiceName<LogStream> serviceName = context.getServiceName();
            final LogStream logStream = context.getRaft().getLogStream();
            final LogStreamService service = new LogStreamService(logStream);

            final ServiceName<LogStream> streamGroup = Protocol.SYSTEM_TOPIC_BUF.equals(logStream.getTopicName()) ?
                    LogStreamServiceNames.SYSTEM_STREAM_GROUP :
                    LogStreamServiceNames.WORKFLOW_STREAM_GROUP;

            final CompletableFuture<Void> future =
                context.getServiceContainer()
                       .createService(serviceName, service)
                       .dependency(ACTOR_SCHEDULER_SERVICE)
                       .dependency(CLUSTER_MANAGER_SERVICE)
                       .dependency(context.raftServiceName)
                       .group(streamGroup)
                       .install();

            context.setServiceFuture(future);

            context.take(TRANSITION_DEFAULT);

            return 1;
        }

        @Override
        public boolean isInterruptable()
        {
            return false;
        }
    }

    static class AwaitServiceFutureState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            int workCount = 0;

            final CompletableFuture<Void> future = context.getServiceFuture();
            if (future != null && future.isDone())
            {
                workCount++;

                try
                {
                    future.get();
                    context.take(TRANSITION_DEFAULT);
                }
                catch (final Throwable t)
                {
                    context.take(TRANSITION_FAILED);
                }
                finally
                {
                    context.setServiceFuture(null);
                }
            }
            else if (future == null)
            {
                context.take(TRANSITION_DEFAULT);
            }

            return workCount;
        }

        @Override
        public boolean isInterruptable()
        {
            return false;
        }
    }

    static class OpenState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            int workCount = 0;

            if (!context.isRaftLeader())
            {
                workCount++;
                context.take(TRANSITION_CLOSE);
            }

            return workCount;
        }

    }
    static class StopLogStreamServiceState implements State<Context>
    {


        @Override
        public int doWork(final Context context) throws Exception
        {
            final ServiceName<LogStream> serviceName = context.getServiceName();

            final ServiceContainer serviceContainer = context.getServiceContainer();
            if (serviceContainer.hasService(serviceName))
            {
                context.setServiceFuture(serviceContainer.removeService(serviceName));
            }

            context.take(TRANSITION_DEFAULT);

            return 1;
        }

        @Override
        public boolean isInterruptable()
        {
            return false;
        }
    }

    static class ClosedState implements State<Context>
    {

        @Override
        public int doWork(final Context context) throws Exception
        {
            int workCount = 0;

            if (context.isRaftLeader())
            {
                workCount++;
                context.take(TRANSITION_OPEN);
            }

            return workCount;
        }

    }

    static class Context extends SimpleStateMachineContext
    {

        private final Raft raft;
        private final ServiceName<Raft> raftServiceName;
        private final ServiceContainer serviceContainer;
        private final ServiceName<LogStream> serviceName;
        private CompletableFuture<Void> serviceFuture;

        Context(final StateMachine<Context> stateMachine, final ServiceName<Raft> raftServiceName, final Raft raft, final ServiceContainer serviceContainer)
        {
            super(stateMachine);
            this.raftServiceName = raftServiceName;
            this.raft = raft;
            this.serviceContainer = serviceContainer;
            this.serviceName = logStreamServiceName(raft.getLogStream().getLogName());

            reset();
        }

        @Override
        public void reset()
        {
            serviceFuture = null;
        }

        public Raft getRaft()
        {
            return raft;
        }

        public ServiceContainer getServiceContainer()
        {
            return serviceContainer;
        }

        public CompletableFuture<Void> getServiceFuture()
        {
            return serviceFuture;
        }

        public void setServiceFuture(final CompletableFuture<Void> serviceFuture)
        {
            this.serviceFuture = serviceFuture;
        }

        public boolean isRaftLeader()
        {
            return raft.getState() == RaftState.LEADER && raft.isInitialEventCommitted();
        }

        public ServiceName<LogStream> getServiceName()
        {
            return serviceName;
        }

    }

}
