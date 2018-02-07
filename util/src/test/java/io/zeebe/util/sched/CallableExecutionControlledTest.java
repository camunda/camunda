package io.zeebe.util.sched;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;

import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;

public class CallableExecutionControlledTest

{
    @Rule
    public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

    @Test
    public void shouldCompleteFutureOnException() throws Exception
    {
        // given
        final Exception expected = new Exception();
        final ExceptionActor actor = new ExceptionActor();
        schedulerRule.submitActor(actor);

        final Future<Void> future = actor.failWith(expected);
        schedulerRule.workUntilDone();

        // then/when
        assertThatThrownBy(() -> future.get(1, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCause(expected);

        assertThat(actor.invocations).hasValue(1); // should not resubmit actor job on failure

    }

    protected static class ExceptionActor extends ZbActor
    {
        protected AtomicInteger invocations = new AtomicInteger(0);

        public Future<Void> failWith(Exception e)
        {
            return actor.call(() ->
            {
                invocations.incrementAndGet();
                throw e;
            });
        }
    }

}
