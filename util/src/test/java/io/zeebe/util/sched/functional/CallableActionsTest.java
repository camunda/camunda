/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util.sched.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;

public class CallableActionsTest {
  @Rule
  public final ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  @Test
  public void shouldCompleteFutureOnException() throws Exception {
    // given
    final Exception expected = new Exception();
    final ExceptionActor actor = new ExceptionActor();
    schedulerRule.submitActor(actor);

    final Future<Void> future = actor.failWith(expected);
    schedulerRule.workUntilDone();

    // then/when
    assertThatThrownBy(() -> future.get(1, TimeUnit.MILLISECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasCause(expected);

    assertThat(actor.invocations).hasValue(1); // should not resubmit actor job on failure
  }

  @Test
  public void shouldCompleteFutureExceptionallyWhenCalledAfterActorClosed() {
    // given
    final CloseableActor actor = new CloseableActor();
    schedulerRule.submitActor(actor);

    actor.close();
    schedulerRule.workUntilDone();

    // when
    final ActorFuture<Void> future = actor.doCall();
    schedulerRule.workUntilDone();

    // then
    assertThat(future).isDone();
    assertThatThrownBy(() -> future.get())
        .isInstanceOf(ExecutionException.class)
        .hasMessage("Actor is closed");
  }

  @Test
  public void shouldCompleteFutureExceptionallyWhenActorClosesAfterSubmission() {
    // given
    final CloseableActor actor = new CloseableActor();
    schedulerRule.submitActor(actor);

    actor.close();
    final ActorFuture<Void> future = actor.doCall();

    // when
    schedulerRule.workUntilDone();

    // then
    assertThat(future).isDone();
    assertThatThrownBy(() -> future.get())
        .isInstanceOf(ExecutionException.class)
        .hasMessage("Actor is closed");
  }

  protected static class ExceptionActor extends Actor {
    protected AtomicInteger invocations = new AtomicInteger(0);

    public Future<Void> failWith(Exception e) {
      return actor.call(
          () -> {
            invocations.incrementAndGet();
            throw e;
          });
    }
  }

  class CloseableActor extends Actor {
    ActorFuture<Void> doCall() {
      return actor.call(() -> {});
    }

    void close() {
      actor.close();
    }
  }
}
