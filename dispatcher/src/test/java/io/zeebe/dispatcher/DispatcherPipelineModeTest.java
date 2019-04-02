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
package io.zeebe.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class DispatcherPipelineModeTest {
  public static final FragmentHandler NOOP_FRAGMENT_HANDLER =
      (buffer, offset, length, streamId, isMarkedFailed) -> FragmentHandler.CONSUME_FRAGMENT_RESULT;

  protected ControlledActorSchedulerRule scheduler = new ControlledActorSchedulerRule();
  protected AutoCloseableRule closeables = new AutoCloseableRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(scheduler).around(closeables);

  protected Dispatcher buildDispatcher() {
    return buildDispatcher(b -> {});
  }

  protected Dispatcher buildDispatcher(Consumer<DispatcherBuilder> configurator) {
    final DispatcherBuilder builder =
        Dispatchers.create("foo")
            .actorScheduler(scheduler.get())
            .modePipeline()
            .bufferSize(ByteValue.ofMegabytes(1));

    configurator.accept(builder);
    final Dispatcher d = builder.build();
    closeables.manage(
        () -> {
          d.closeAsync();
          scheduler.workUntilDone();
        });
    return d;
  }

  @Test
  public void shouldGetPredefinedSubscriptions() throws InterruptedException, ExecutionException {
    // given
    final Dispatcher dispatcher = buildDispatcher(b -> b.subscriptions("s1", "s2"));
    final Future<Subscription> future1 = dispatcher.getSubscriptionAsync("s1");
    final Future<Subscription> future2 = dispatcher.getSubscriptionAsync("s2");

    // when
    scheduler.workUntilDone();

    // when
    final Subscription subscription1 = future1.get();
    assertThat(subscription1).isNotNull();
    assertThat(subscription1.getName()).isEqualTo("s1");
    assertThat(subscription1.getId()).isEqualTo(0);

    // then
    final Subscription subscription2 = future2.get();
    assertThat(subscription2).isNotNull();
    assertThat(subscription2.getName()).isEqualTo("s2");
    assertThat(subscription2.getId()).isEqualTo(1);
  }

  @Test
  public void shouldThrowExceptionForNonExistingSubscription()
      throws InterruptedException, ExecutionException {
    // given
    final Dispatcher dispatcher = buildDispatcher();
    final Future<Subscription> future = dispatcher.getSubscriptionAsync("nonExisting");

    scheduler.workUntilDone();

    // then
    assertThatThrownBy(() -> future.get())
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(RuntimeException.class);
  }

  @Test
  public void shouldNotOpenSubscription() throws InterruptedException, ExecutionException {
    // given
    final Dispatcher dispatcher = buildDispatcher();
    final Future<Subscription> future = dispatcher.openSubscriptionAsync("new");

    scheduler.workUntilDone();

    // when/then
    assertThatThrownBy(() -> future.get())
        .isInstanceOf(ExecutionException.class)
        .hasMessage("Cannot open subscriptions in pipelining mode")
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldNotCloseSubscription() throws InterruptedException, ExecutionException {
    // given
    final Dispatcher dispatcher = buildDispatcher(b -> b.subscriptions("s1"));
    final Future<Subscription> openFuture = dispatcher.getSubscriptionAsync("s1");
    scheduler.workUntilDone();

    final Subscription subscription = openFuture.get();

    final Future<Void> closeFuture = dispatcher.closeSubscriptionAsync(subscription);
    scheduler.workUntilDone();

    // when/then
    assertThatThrownBy(() -> closeFuture.get())
        .isInstanceOf(ExecutionException.class)
        .hasMessage("Cannot close subscriptions in pipelining mode")
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldNotReadBeyondPreviousSubscription()
      throws InterruptedException, ExecutionException {
    // given
    final Dispatcher dispatcher = buildDispatcher(b -> b.subscriptions("s1", "s2"));

    final Future<Subscription> future1 = dispatcher.getSubscriptionAsync("s1");
    final Future<Subscription> future2 = dispatcher.getSubscriptionAsync("s2");
    scheduler.workUntilDone();

    future1.get();
    final Subscription subscription2 = future2.get();

    // when
    final int fragmentsRead = subscription2.poll(NOOP_FRAGMENT_HANDLER, 1);

    // then
    assertThat(fragmentsRead).isEqualTo(0);
  }

  @Test
  public void shouldUpdatePublisherLimit() throws InterruptedException, ExecutionException {
    // given
    final Dispatcher dispatcher =
        buildDispatcher(b -> b.bufferSize(ByteValue.ofMegabytes(1)).subscriptions("s1", "s2"));

    final Future<Subscription> future1 = dispatcher.getSubscriptionAsync("s1");
    final Future<Subscription> future2 = dispatcher.getSubscriptionAsync("s2");
    scheduler.workUntilDone();

    final Subscription subscription1 = future1.get();
    final Subscription subscription2 = future2.get();

    final int messageLength = 32;

    publishMessages(dispatcher, 2, messageLength);
    scheduler.workUntilDone();

    final long initialPublisherLimit = dispatcher.getPublisherLimit();

    // consuming one fragment
    subscription1.poll(NOOP_FRAGMENT_HANDLER, 2);
    subscription2.poll(NOOP_FRAGMENT_HANDLER, 1);

    final long expectedPublisherLimit =
        initialPublisherLimit + DataFrameDescriptor.alignedFramedLength(messageLength);

    // when
    scheduler.workUntilDone();

    // then
    assertThat(dispatcher.getPublisherLimit()).isEqualTo(expectedPublisherLimit);
  }

  protected void publishMessages(Dispatcher dispatcher, int numMessages, int length) {
    for (int i = 0; i < numMessages; i++) {
      long position = -2;

      do {
        position = dispatcher.offer(bufferOfLength(length));
      } while (position == -2);

      if (position < 0) {
        throw new RuntimeException("Could not publish message " + i);
      }
    }
  }

  protected static DirectBuffer bufferOfLength(int length) {
    return new UnsafeBuffer(new byte[length]);
  }
}
