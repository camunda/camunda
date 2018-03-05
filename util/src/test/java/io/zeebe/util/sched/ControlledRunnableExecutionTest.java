package io.zeebe.util.sched;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;

public class ControlledRunnableExecutionTest
{

    @Rule
    public ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();

    @Test
    public void shouldInvokeRunFromWithinActor()
    {
        // given
        final Runner runner = new Runner()
        {
            @Override
            protected void onActorStarted()
            {
                this.doRun();
            }
        };

        scheduler.submitActor(runner);

        // when
        scheduler.workUntilDone();

        // then
        assertThat(runner.runs).isEqualTo(1);
    }

    @Test
    public void shouldInvokeRunFromAnotherActor()
    {
        // given
        final Runner runner = new Runner();
        final ZbActor invoker = new ZbActor()
        {
            protected void onActorStarted()
            {
                runner.doRun();
            }
        };

        scheduler.submitActor(runner);
        scheduler.submitActor(invoker);

        // when
        scheduler.workUntilDone();

        // then
        assertThat(runner.runs).isEqualTo(1);
    }

    @Test
    public void shouldSubmitRunnableToCorrectActorTask()
    {
        // given
        final List<ZbActor> actorContext = new ArrayList<>();
        final Runner runner = new Runner(() -> actorContext.add(ActorThread.current().getCurrentTask().getActor()));

        final ZbActor invoker = new ZbActor()
        {
            protected void onActorStarted()
            {
                runner.doRun();
            }
        };

        scheduler.submitActor(runner);
        scheduler.submitActor(invoker);

        // when
        scheduler.workUntilDone();

        // then
        assertThat(actorContext).containsExactly(runner);
    }

    @Test
    public void shouldNotInvokeRunFromOutside()
    {
        // given
        final Runner runner = new Runner();

        // when/then
        assertThatThrownBy(() -> runner.doRun())
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Incorrect usage of actor. run(...): must be called from actor thread");
    }

    class Runner extends ZbActor
    {
        int runs = 0;
        Runnable onExecution;

        Runner()
        {
            this(null);
        }

        Runner(Runnable onExecution)
        {
            this.onExecution = onExecution;
        }

        public void doRun()
        {
            actor.run(() ->
            {
                if (onExecution != null)
                {
                    onExecution.run();
                }
                runs++;
            });
        }
    }
}

