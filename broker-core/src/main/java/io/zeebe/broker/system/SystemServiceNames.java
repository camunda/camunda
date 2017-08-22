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
package io.zeebe.broker.system;

import io.zeebe.broker.services.Counters;
import io.zeebe.broker.system.executor.ScheduledExecutor;
import io.zeebe.broker.system.log.SystemPartitionManager;
import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.actor.ActorScheduler;

public class SystemServiceNames
{
    public static final ServiceName<ActorScheduler> ACTOR_SCHEDULER_SERVICE = ServiceName.newServiceName("broker.task.scheduler", ActorScheduler.class);

    public static final ServiceName<Counters> COUNTERS_MANAGER_SERVICE = ServiceName.newServiceName("broker.countersManager", Counters.class);

    public static final ServiceName<ScheduledExecutor> EXECUTOR_SERVICE = ServiceName.newServiceName("broker.executor", ScheduledExecutor.class);

    public static final ServiceName<SystemPartitionManager> SYSTEM_LOG_MANAGER = ServiceName.newServiceName("broker.system.log", SystemPartitionManager.class);
    public static final ServiceName<StreamProcessorController> SYSTEM_PROCESSOR = ServiceName.newServiceName("broker.system.log.processor", StreamProcessorController.class);

}
