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
package io.zeebe.broker.clustering.base.snapshots;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class SnapshotReplicationInstallServiceTest
{
    private ControlledActorSchedulerRule actorSchedulerRule = new ControlledActorSchedulerRule();
    private ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);

    @Rule
    public RuleChain ruleChain =
            RuleChain.outerRule(actorSchedulerRule)
                    .around(serviceContainerRule);
    @Test
    public void shouldInstallReplicationServiceOnNewFollowerPartition()
    {
        // given
        final BrokerCfg config = new BrokerCfg();
        final SnapshotReplicationInstallService installService = new SnapshotReplicationInstallService(config);
        final PartitionInfo info = new PartitionInfo(BufferUtil.wrapString("test"), 1, 1);
        final Partition partition = new Partition(info, RaftState.FOLLOWER);
        final String partitionName = String.format("%s-%d", info.getTopicName(), info.getPartitionId());
        final ServiceName<SnapshotReplicationService> serviceName = snapshotReplicationServiceName(partition);

        // when
        serviceContainerRule.get().createService(SNAPSHOT_REPLICATION_INSTALL_SERVICE_NAME, installService)
                .groupReference(FOLLOWER_PARTITION_GROUP_NAME, installService.getFollowerPartitionsGroupReference())
                .install();
        actorSchedulerRule.workUntilDone();

        // then
        assertThat(serviceContainerRule.get().hasService(serviceName)).isFalse();

        // when
        serviceContainerRule.get().createService(followerPartitionServiceName(partitionName), partition)
                .group(FOLLOWER_PARTITION_GROUP_NAME)
                .install();
        actorSchedulerRule.workUntilDone();

        // then
        assertThat(serviceContainerRule.get().hasService(serviceName)).isTrue();
    }
}
