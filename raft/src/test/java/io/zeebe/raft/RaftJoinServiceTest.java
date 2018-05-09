package io.zeebe.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.raft.state.RaftState;
import io.zeebe.raft.util.RaftClusterRule;
import io.zeebe.raft.util.RaftRule;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Rule;
import org.junit.Test;

public class RaftJoinServiceTest
{
    public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
    public ServiceContainerRule serviceContainerRule = new ServiceContainerRule(actorSchedulerRule);

    public RaftRule raft1 = new RaftRule(serviceContainerRule, "localhost", 8001, "default", 0);
    public RaftRule raft2 = new RaftRule(serviceContainerRule, "localhost", 8002, "default", 0, true, raft1);

    @Rule
    public RaftClusterRule cluster = new RaftClusterRule(actorSchedulerRule, serviceContainerRule, raft1, raft2);

    @Test
    public void shouldBeInterruptedIfShutdownDuringStart()
    {
        // given
        cluster.awaitRaftState(raft1, RaftState.LEADER);

        // when
        raft1.closeRaft();

        // then
        assertThat(raft1.isClosed()).isTrue();
        assertThat(raft1.getMemberSize()).isEqualTo(1);
        assertThat(raft2.isJoined()).isFalse();
    }
}
