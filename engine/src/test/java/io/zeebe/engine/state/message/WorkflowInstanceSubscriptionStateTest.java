/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.state.message;

import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.ZeebeStateRule;
import io.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class WorkflowInstanceSubscriptionStateTest {

  @Rule public ZeebeStateRule stateRule = new ZeebeStateRule();

  private WorkflowInstanceSubscriptionState state;

  @Before
  public void setUp() {
    state = stateRule.getZeebeState().getWorkflowInstanceSubscriptionState();
  }

  @Test
  public void shouldNotExist() {
    // given
    final WorkflowInstanceSubscription subscription = subscriptionWithElementInstanceKey(1);
    state.put(subscription);

    // when
    final boolean exist =
        state.existSubscriptionForElementInstance(2, subscription.getMessageName());

    // then
    assertThat(exist).isFalse();
  }

  @Test
  public void shouldExistSubscription() {
    // given
    final WorkflowInstanceSubscription subscription = subscriptionWithElementInstanceKey(1);
    state.put(subscription);

    // when
    final boolean exist =
        state.existSubscriptionForElementInstance(1, subscription.getMessageName());

    // then
    assertThat(exist).isTrue();
  }

  @Test
  public void shouldNoVisitSubscriptionBeforeTime() {
    // given
    final WorkflowInstanceSubscription subscription1 = subscriptionWithElementInstanceKey(1L);
    state.put(subscription1);
    state.updateSentTime(subscription1, 1_000);

    final WorkflowInstanceSubscription subscription2 = subscriptionWithElementInstanceKey(2L);
    state.put(subscription2);
    state.updateSentTime(subscription2, 3_000);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(1_000, s -> keys.add(s.getElementInstanceKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldVisitSubscriptionBeforeTime() {
    // given
    final WorkflowInstanceSubscription subscription1 = subscriptionWithElementInstanceKey(1L);
    state.put(subscription1);
    state.updateSentTime(subscription1, 1_000);

    final WorkflowInstanceSubscription subscription2 = subscriptionWithElementInstanceKey(2L);
    state.put(subscription2);
    state.updateSentTime(subscription2, 3_000);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldFindSubscriptionBeforeTimeInOrder() {
    // given
    final WorkflowInstanceSubscription subscription1 = subscriptionWithElementInstanceKey(1L);
    state.put(subscription1);
    state.updateSentTime(subscription1, 1_000);

    final WorkflowInstanceSubscription subscription2 = subscriptionWithElementInstanceKey(2L);
    state.put(subscription2);
    state.updateSentTime(subscription2, 2_000);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(3_000, s -> keys.add(s.getElementInstanceKey()));

    assertThat(keys).hasSize(2).containsExactly(1L, 2L);
  }

  @Test
  public void shouldNotVisitSubscriptionIfOpened() {
    // given
    final WorkflowInstanceSubscription subscription1 = subscriptionWithElementInstanceKey(1L);
    state.put(subscription1);

    final WorkflowInstanceSubscription subscription2 = subscriptionWithElementInstanceKey(2L);
    state.put(subscription2);
    state.updateToOpenedState(subscription2, 3);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateSubscriptionSentTime() {
    // given
    final WorkflowInstanceSubscription subscription = subscriptionWithElementInstanceKey(1L);
    state.put(subscription);

    // when
    state.updateSentTime(subscription, 1_000);

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);

    // and
    state.updateSentTime(subscription, 1_500);

    keys.clear();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldUpdateOpenState() {
    // given
    final WorkflowInstanceSubscription subscription = subscriptionWithElementInstanceKey(1L);
    state.put(subscription);

    Assertions.assertThat(subscription.isOpening()).isTrue();

    // when
    state.updateToOpenedState(subscription, 3);

    // then
    Assertions.assertThat(subscription.isOpening()).isFalse();

    // and
    assertThat(
            state.getSubscription(1L, subscription.getMessageName()).getSubscriptionPartitionId())
        .isEqualTo(3);

    // and
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getElementInstanceKey()));

    assertThat(keys).isEmpty();
  }

  @Test
  public void shouldUpdateCloseState() {
    // given
    final WorkflowInstanceSubscription subscription = subscriptionWithElementInstanceKey(1L);
    state.put(subscription);

    state.updateToOpenedState(subscription, 3);

    Assertions.assertThat(subscription.isClosing()).isFalse();

    // when
    state.updateToClosingState(subscription, 1_000);

    // then
    assertThat(state.getSubscription(1L, subscription.getMessageName()).isClosing()).isTrue();
    // and
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getElementInstanceKey()));

    assertThat(keys).hasSize(1).contains(1L);
  }

  @Test
  public void shouldRemoveSubscription() {
    // given
    final WorkflowInstanceSubscription subscription = subscriptionWithElementInstanceKey(1L);
    state.put(subscription);
    state.updateSentTime(subscription, 1_000);

    // when
    state.remove(1L, subscription.getMessageName());

    // then
    final List<Long> keys = new ArrayList<>();
    state.visitSubscriptionBefore(2_000, s -> keys.add(s.getElementInstanceKey()));

    assertThat(keys).isEmpty();

    // and
    assertThat(state.existSubscriptionForElementInstance(1L, subscription.getMessageName()))
        .isFalse();
  }

  @Test
  public void shouldNotFailOnRemoveSubscriptionTwice() {
    // given
    final WorkflowInstanceSubscription subscription = subscriptionWithElementInstanceKey(1L);
    state.put(subscription);

    // when
    state.remove(1L, subscription.getMessageName());
    state.remove(1L, subscription.getMessageName());

    // then
    assertThat(state.existSubscriptionForElementInstance(1L, subscription.getMessageName()))
        .isFalse();
  }

  @Test
  public void shouldNotRemoveSubscriptionOnDifferentKey() {
    // given
    state.put(subscription("messageName", "correlationKey", 1L));
    state.put(subscription("messageName", "correlationKey", 2L));

    // when
    state.remove(2L, wrapString("messageName"));

    // then
    assertThat(state.existSubscriptionForElementInstance(1L, wrapString("messageName"))).isTrue();
  }

  @Test
  public void shouldVisitAllSubscriptionsInTheState() {
    // given
    state.put(subscription("message1", "correlationKey", 1L));
    state.put(subscription("message2", "correlationKey", 1L));
    state.put(subscription("message3", "correlationKey", 2L));

    // when
    final List<Tuple<Long, DirectBuffer>> visited = new ArrayList<>();
    state.visitElementSubscriptions(
        1L,
        s -> visited.add(new Tuple<>(s.getElementInstanceKey(), cloneBuffer(s.getMessageName()))));

    // then
    assertThat(visited)
        .containsExactly(
            new Tuple<>(1L, wrapString("message1")), new Tuple<>(1L, wrapString("message2")));
  }

  private WorkflowInstanceSubscription subscriptionWithElementInstanceKey(long elementInstanceKey) {
    return subscription("handler", "messageName", "correlationKey", elementInstanceKey);
  }

  private WorkflowInstanceSubscription subscription(
      String name, String correlationKey, long elementInstanceKey) {
    return subscription("handler", name, correlationKey, elementInstanceKey);
  }

  private WorkflowInstanceSubscription subscription(
      String handlerId, String name, String correlationKey, long elementInstanceKey) {
    return new WorkflowInstanceSubscription(
        1L,
        elementInstanceKey,
        wrapString(handlerId),
        wrapString(name),
        wrapString(correlationKey),
        1_000,
        true);
  }
}
