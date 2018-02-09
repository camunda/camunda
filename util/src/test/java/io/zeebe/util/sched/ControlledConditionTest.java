package io.zeebe.util.sched;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;

import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;

public class ControlledConditionTest
{

    @Rule
    public ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();

    @Test
    public void shouldTerminateOnFollowUpAndYield()
    {
        fail("infinite loop");

        // given
        final AtomicInteger invocations = new AtomicInteger(0);
        final AtomicReference<ActorCondition> condition = new AtomicReference<>();

        final ZbActor actor = new ZbActor()
        {
            @Override
            protected void onActorStarted()
            {
                condition.set(actor.onCondition("foo", this::onCondition));
            }

            protected void onCondition()
            {
                invocations.incrementAndGet();
                actor.run(this::doNothing);
                actor.yield();
            }

            protected void doNothing()
            {
            }
        };

        scheduler.submitActor(actor);
        scheduler.workUntilDone();

        // when
        condition.get().signal();
        scheduler.workUntilDone();

        // then
        assertThat(invocations.get()).isEqualTo(1);

    }
}
