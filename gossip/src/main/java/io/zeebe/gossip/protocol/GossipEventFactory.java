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
package io.zeebe.gossip.protocol;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.gossip.Loggers;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.dissemination.MembershipCustomEventUpdater;
import io.zeebe.gossip.dissemination.MembershipEventUpdater;
import io.zeebe.gossip.dissemination.MembershipListEventSupplier;
import io.zeebe.gossip.membership.MembershipList;
import java.util.Iterator;
import org.slf4j.Logger;

public class GossipEventFactory {
  private final GossipConfiguration configuration;
  private final MembershipList membershipList;
  private final DisseminationComponent disseminationComponent;
  private final CustomEventSupplier customEventSyncResponseSupplier;
  private final CustomEventConsumer customEventListenerConsumer;

  private final MembershipEventUpdater membershipEventUpdater;
  private final MembershipCustomEventUpdater membershipCustomEventUpdater;

  // for failure analysis and tests
  private final MembershipEventLogger membershipEventLogger = new MembershipEventLogger();
  private final CustomEventLogger customEventLogger = new CustomEventLogger();

  public GossipEventFactory(
      GossipConfiguration configuration,
      MembershipList membershipList,
      DisseminationComponent disseminationComponent,
      CustomEventSupplier customEventSyncResponseSupplier,
      CustomEventConsumer customEventListenerConsumer) {
    this.configuration = configuration;
    this.membershipList = membershipList;
    this.disseminationComponent = disseminationComponent;
    this.customEventSyncResponseSupplier = customEventSyncResponseSupplier;
    this.customEventListenerConsumer = customEventListenerConsumer;

    this.membershipEventUpdater =
        new MembershipEventUpdater(membershipList, disseminationComponent);
    this.membershipCustomEventUpdater = new MembershipCustomEventUpdater(membershipList);
  }

  /** Create a gossip event for PING, ACK or PING-REQ. */
  public GossipEvent createFailureDetectionEvent() {
    // read events from dissemination buffer
    final MembershipEventSupplier membershipEventSupplier = disseminationComponent;
    final CustomEventSupplier customEventSupplier = disseminationComponent;

    // update membership list and add events to dissemination buffer if membership has changed
    final MembershipEventConsumer membershipEventConsumer =
        membershipEventLogger.andThen(membershipEventUpdater).andThen(disseminationComponent);

    // update custom event references in membership list
    // if an event is new then invoke the listeners and add it to dissemination buffer
    final CustomEventConsumer customEventConsumer =
        customEventLogger
            .andThen(membershipCustomEventUpdater)
            .andThen(customEventListenerConsumer)
            .andThen(disseminationComponent);

    return new GossipEvent(
        membershipEventSupplier,
        membershipEventConsumer,
        customEventSupplier,
        customEventConsumer,
        configuration.getMaxMembershipEventsPerMessage(),
        configuration.getMaxCustomEventsPerMessage());
  }

  public GossipEvent createSyncRequestEvent() {
    // sync request should not contain any data
    return new GossipEvent(
        new EmptyMembershipEventSupplier(),
        event -> false,
        new EmptyCustomEventSupplier(),
        event -> false,
        0,
        0);
  }

  public GossipEvent createSyncResponseEvent() {
    // add all members from list as events
    final MembershipEventSupplier membershipEventSupplier =
        new MembershipListEventSupplier(membershipList);

    // get custom events from registered sync handlers
    final CustomEventSupplier customEventSupplier = customEventSyncResponseSupplier;

    // update membership list
    final MembershipEventConsumer membershipEventConsumer =
        membershipEventLogger.andThen(membershipEventUpdater).andThen(disseminationComponent);

    // update custom event references in membership list and invoke the listeners
    final CustomEventConsumer customEventConsumer =
        customEventLogger
            .andThen(membershipCustomEventUpdater)
            .andThen(customEventListenerConsumer)
            .andThen(disseminationComponent);

    return new GossipEvent(
        membershipEventSupplier,
        membershipEventConsumer,
        customEventSupplier,
        customEventConsumer,
        Integer.MAX_VALUE,
        Integer.MAX_VALUE);
  }

  public GossipEvent createAckResponse() {
    return createFailureDetectionEvent();
  }

  public GossipEvent createSyncResponse() {
    return createSyncResponseEvent();
  }

  private static final class MembershipEventLogger implements MembershipEventConsumer {
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    @Override
    public boolean consumeMembershipEvent(MembershipEvent event) {
      if (LOG.isTraceEnabled()) {
        LOG.trace(
            "Received membership event with memberId: '{}', type: {}, gossip-term: {}",
            event.getMemberId(),
            event.getType(),
            event.getGossipTerm());
      }

      return true;
    }
  }

  private static final class CustomEventLogger implements CustomEventConsumer {
    private static final Logger LOG = Loggers.GOSSIP_LOGGER;

    @Override
    public boolean consumeCustomEvent(CustomEvent event) {
      if (LOG.isTraceEnabled()) {
        LOG.trace(
            "Received custom event of type '{}', sender-id: '{}', gossip-term: {}",
            bufferAsString(event.getType()),
            event.getSenderId(),
            event.getSenderGossipTerm());
      }

      return true;
    }
  }

  private static final class EmptyMembershipEventSupplier implements MembershipEventSupplier {
    private static final Iterator<MembershipEvent> ITERATOR = new EmptyIterator<>();

    @Override
    public int membershipEventSize() {
      return 0;
    }

    @Override
    public Iterator<MembershipEvent> membershipEventViewIterator(int max) {
      return ITERATOR;
    }

    @Override
    public Iterator<MembershipEvent> membershipEventDrainIterator(int max) {
      return ITERATOR;
    }
  }

  private static final class EmptyCustomEventSupplier implements CustomEventSupplier {
    private static final Iterator<CustomEvent> ITERATOR = new EmptyIterator<>();

    @Override
    public int customEventSize() {
      return 0;
    }

    @Override
    public Iterator<CustomEvent> customEventViewIterator(int max) {
      return ITERATOR;
    }

    @Override
    public Iterator<CustomEvent> customEventDrainIterator(int max) {
      return ITERATOR;
    }
  }

  private static final class EmptyIterator<T> implements Iterator<T> {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public T next() {
      return null;
    }
  }
}
