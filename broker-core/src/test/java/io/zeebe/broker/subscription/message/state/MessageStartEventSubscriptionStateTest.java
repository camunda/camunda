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
package io.zeebe.broker.subscription.message.state;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.subscription.message.data.MessageStartEventSubscriptionRecord;
import io.zeebe.broker.util.ZeebeStateRule;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MessageStartEventSubscriptionStateTest {

  @Rule public ZeebeStateRule stateRule = new ZeebeStateRule();

  private MessageStartEventSubscriptionState state;

  @Before
  public void setUp() {
    state = stateRule.getZeebeState().getMessageStartEventSubscriptionState();
  }

  @Test
  public void shouldExistAfterPut() {
    final MessageStartEventSubscriptionRecord subscription =
        createSubscription("messageName", "startEventID", 1);
    state.put(subscription);
    assertThat(state.exists(subscription)).isTrue();
  }

  @Test
  public void shouldNotExistForDifferentKey() {
    final MessageStartEventSubscriptionRecord subscription =
        createSubscription("messageName", "startEventID", 1);
    state.put(subscription);

    subscription.setWorkflowKey(2);
    assertThat(state.exists(subscription)).isFalse();
  }

  @Test
  public void shouldVisitForMessageNames() {
    final MessageStartEventSubscriptionRecord subscription1 =
        createSubscription("message", "startEvent1", 1);
    state.put(subscription1);

    // more subscriptions for same message
    final MessageStartEventSubscriptionRecord subscription2 =
        createSubscription("message", "startEvent2", 2);
    state.put(subscription2);

    final MessageStartEventSubscriptionRecord subscription3 =
        createSubscription("message", "startEvent3", 3);
    state.put(subscription3);

    // should not visit other message
    final MessageStartEventSubscriptionRecord subscription4 =
        createSubscription("message-other", "startEvent4", 3);
    state.put(subscription4);

    final List<String> visitedStartEvents = new ArrayList<>();

    state.visitSubscriptionsByMessageName(
        wrapString("message"),
        subscription -> {
          visitedStartEvents.add(bufferAsString(subscription.getStartEventId()));
        });

    assertThat(visitedStartEvents.size()).isEqualTo(3);
    assertThat(visitedStartEvents)
        .containsExactlyInAnyOrder("startEvent1", "startEvent2", "startEvent3");
  }

  @Test
  public void shouldNotExistAfterRemove() {
    final MessageStartEventSubscriptionRecord subscription1 =
        createSubscription("message1", "startEvent1", 1);
    state.put(subscription1);

    // more subscriptions for same workflow
    final MessageStartEventSubscriptionRecord subscription2 =
        createSubscription("message2", "startEvent2", 1);
    state.put(subscription2);

    final MessageStartEventSubscriptionRecord subscription3 =
        createSubscription("message3", "startEvent3", 1);
    state.put(subscription3);

    state.removeSubscriptionsOfWorkflow(1);

    assertThat(state.exists(subscription1)).isFalse();
    assertThat(state.exists(subscription2)).isFalse();
    assertThat(state.exists(subscription3)).isFalse();
  }

  @Test
  public void shouldNotRemoveOtherKeys() {
    final MessageStartEventSubscriptionRecord subscription1 =
        createSubscription("message1", "startEvent1", 1);
    state.put(subscription1);

    final MessageStartEventSubscriptionRecord subscription2 =
        createSubscription("message1", "startEvent1", 4);
    state.put(subscription2);

    state.removeSubscriptionsOfWorkflow(1);

    assertThat(state.exists(subscription1)).isFalse();
    assertThat(state.exists(subscription2)).isTrue();
  }

  private MessageStartEventSubscriptionRecord createSubscription(
      String messageName, String startEventId, long key) {
    return new MessageStartEventSubscriptionRecord()
        .setStartEventId(wrapString(startEventId))
        .setMessageName(wrapString(messageName))
        .setWorkflowKey(key);
  }
}
