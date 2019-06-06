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
package io.zeebe.engine.state.instance;

import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.util.ZeebeStateRule;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class EventScopeInstanceStateTest {

  @Rule public ZeebeStateRule stateRule = new ZeebeStateRule();

  private EventScopeInstanceState state;

  @Before
  public void setUp() {
    final ZeebeState zeebeState = stateRule.getZeebeState();
    state = zeebeState.getWorkflowState().getEventScopeInstanceState();
  }

  @Test
  public void shouldCreateInterruptingEventScopeInstance() {
    // given
    final long key = 123;

    // when
    state.createIfNotExists(key, Collections.singleton(wrapString("foo")));

    // then
    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isTrue();
    Assertions.assertThat(instance.isInterrupting(wrapString("foo"))).isTrue();
  }

  @Test
  public void shouldCreateNonInterruptingEventScopeInstance() {
    // given
    final long key = 123;

    // when
    state.createIfNotExists(key, Collections.singleton(wrapString("foo")));

    // then
    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isTrue();
    Assertions.assertThat(instance.isInterrupting(wrapString("bar"))).isFalse();
  }

  @Test
  public void shouldTriggerInterruptingEventScopeInstance() {
    // given
    final long key = 123;
    final long eventKey = 456;
    final EventTrigger eventTrigger = createEventTrigger();

    state.createIfNotExists(key, Collections.singleton(eventTrigger.getElementId()));

    // when
    final boolean triggered = triggerEvent(key, eventKey, eventTrigger);

    // then
    assertThat(triggered).isTrue();

    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger);

    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isFalse();
  }

  @Test
  public void shouldTriggerInterruptingEventScopeInstanceOnlyOnce() {
    // given
    final long key = 123;
    final long eventKey1 = 456;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final long eventKey2 = 789;
    final EventTrigger eventTrigger2 = createEventTrigger();

    state.createIfNotExists(key, Collections.singleton(eventTrigger1.getElementId()));

    // when
    final boolean triggered1 = triggerEvent(key, eventKey1, eventTrigger1);
    final boolean triggered2 = triggerEvent(key, eventKey2, eventTrigger2);

    // then
    assertThat(triggered1).isTrue();
    assertThat(triggered2).isFalse();

    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger1);
    Assertions.assertThat(state.pollEventTrigger(key)).isNull();

    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isFalse();
  }

  @Test
  public void shouldTriggerNonInterruptingEventScopeInstanceMultipleTimes() {
    // given
    final long key = 123;
    final long eventKey1 = 456;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final long eventKey2 = 789;
    final EventTrigger eventTrigger2 = createEventTrigger();

    state.createIfNotExists(key, Collections.emptyList());

    // when
    final boolean triggered1 = triggerEvent(key, eventKey1, eventTrigger1);
    final boolean triggered2 = triggerEvent(key, eventKey2, eventTrigger2);

    // then
    assertThat(triggered1).isTrue();
    assertThat(triggered2).isTrue();

    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger1);
    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger2);
    Assertions.assertThat(state.pollEventTrigger(key)).isNull();

    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isTrue();
  }

  @Test
  public void shouldNotTriggerOnNonExistingEventScope() {
    // given
    final EventTrigger eventTrigger = createEventTrigger();

    // when
    final boolean triggered = triggerEvent(123, 456, eventTrigger);

    // then
    assertThat(triggered).isFalse();
  }

  @Test
  public void shouldPeekEventTrigger() {
    // given
    final long key = 123;
    final EventTrigger eventTrigger = createEventTrigger();

    // when
    state.createIfNotExists(key, Collections.emptyList());
    triggerEvent(key, 1, eventTrigger);

    // then
    Assertions.assertThat(state.peekEventTrigger(key)).isEqualTo(eventTrigger);
    Assertions.assertThat(state.peekEventTrigger(key)).isEqualTo(eventTrigger);
  }

  @Test
  public void shouldPollEventTrigger() {
    // given
    final long key = 123;
    final EventTrigger eventTrigger1 = createEventTrigger();
    final EventTrigger eventTrigger2 = createEventTrigger();

    // when
    state.createIfNotExists(key, Collections.emptyList());
    triggerEvent(key, 1, eventTrigger1);
    triggerEvent(key, 2, eventTrigger2);

    // then
    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger1);
    Assertions.assertThat(state.pollEventTrigger(key)).isEqualTo(eventTrigger2);
    Assertions.assertThat(state.pollEventTrigger(key)).isNull();
  }

  @Test
  public void shouldDeleteTrigger() {
    // given
    final long key = 123;
    final long eventKey = 456;

    state.createIfNotExists(key, Collections.emptyList());
    triggerEvent(key, eventKey, createEventTrigger());

    // when
    state.deleteTrigger(key, eventKey);

    // then
    Assertions.assertThat(state.pollEventTrigger(key)).isNull();
  }

  @Test
  public void shouldDeleteEventScopeAndTriggers() {
    // given
    final long key = 123;

    state.createIfNotExists(key, Collections.emptyList());
    triggerEvent(123, 1, createEventTrigger());
    triggerEvent(123, 2, createEventTrigger());
    triggerEvent(123, 3, createEventTrigger());

    // when
    state.deleteInstance(key);

    // then
    Assertions.assertThat(state.getInstance(key)).isNull();
    Assertions.assertThat(state.peekEventTrigger(key)).isNull();
  }

  @Test
  public void shouldNotTriggerOnDeletedEventScope() {
    // given
    final long key = 123;
    final EventTrigger eventTrigger = createEventTrigger();

    state.createIfNotExists(key, Collections.singleton(eventTrigger.getElementId()));
    state.deleteInstance(key);

    // when
    final boolean triggered = triggerEvent(key, 456, eventTrigger);

    // then
    assertThat(triggered).isFalse();
  }

  @Test
  public void shouldShutdownInstance() {
    // given
    final long key = 123;
    state.createIfNotExists(key, Collections.singleton(wrapString("foo")));

    // when
    state.shutdownInstance(key);

    // then
    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance.isAccepting()).isFalse();
  }

  @Test
  public void shouldNotCreateInstanceIfTryingToShutdownNonExistentOne() {
    // given
    final long key = 123;

    // when
    state.shutdownInstance(key);

    // then
    final EventScopeInstance instance = state.getInstance(key);
    Assertions.assertThat(instance).isNull();
  }

  @Test
  public void shouldResetElementInstance() {
    // given
    final long firstKey = 123;
    final long secondKey = 345;
    final DirectBuffer firstId = wrapString("a");
    final Collection<DirectBuffer> firstIds = Collections.singletonList(firstId);

    // when
    state.createIfNotExists(firstKey, firstIds);
    state.createIfNotExists(secondKey, Collections.emptyList());

    // then
    Assertions.assertThat(state.getInstance(firstKey).isInterrupting(firstId)).isTrue();
    Assertions.assertThat(state.getInstance(secondKey).isInterrupting(firstId)).isFalse();
  }

  private boolean triggerEvent(long eventScopeKey, long eventKey, EventTrigger eventTrigger) {
    return state.triggerEvent(
        eventScopeKey, eventKey, eventTrigger.getElementId(), eventTrigger.getVariables());
  }

  private EventTrigger createEventTrigger() {
    return createEventTrigger(randomString(), randomString());
  }

  private EventTrigger createEventTrigger(String elementId, String variables) {
    return new EventTrigger()
        .setElementId(wrapString(elementId))
        .setVariables(wrapString(variables));
  }

  private String randomString() {
    return UUID.randomUUID().toString();
  }
}
