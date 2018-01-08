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
package io.zeebe.broker.clustering;

import io.zeebe.broker.clustering.management.ClusterManager;
import io.zeebe.broker.clustering.management.ClusterManagerContext;
import io.zeebe.broker.clustering.management.memberList.MemberListService;
import io.zeebe.gossip.Gossip;
import io.zeebe.raft.Raft;
import io.zeebe.servicecontainer.ServiceName;

public class ClusterServiceNames
{
    public static final ServiceName<MemberListService> MEMBER_LIST_SERVICE = ServiceName.newServiceName("cluster.member.list", MemberListService.class);
    public static final ServiceName<Gossip> GOSSIP_SERVICE = ServiceName.newServiceName("cluster.gossip", Gossip.class);
    public static final ServiceName<Raft> RAFT_SERVICE_GROUP = ServiceName.newServiceName("cluster.raft.service", Raft.class);
    public static final ServiceName<ClusterManager> CLUSTER_MANAGER_SERVICE = ServiceName.newServiceName("cluster.manager", ClusterManager.class);
    public static final ServiceName<ClusterManagerContext> CLUSTER_MANAGER_CONTEXT_SERVICE = ServiceName.newServiceName("cluster.manager.context", ClusterManagerContext.class);

    public static ServiceName<Raft> raftServiceName(final String name)
    {
        return ServiceName.newServiceName(String.format("cluster.raft.%s", name), Raft.class);
    }

}
