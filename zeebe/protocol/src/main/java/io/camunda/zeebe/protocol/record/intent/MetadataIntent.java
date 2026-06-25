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
package io.camunda.zeebe.protocol.record.intent;

/**
 * Intents for the event-bridge metadata stream: the topic registry (riding {@link
 * io.camunda.zeebe.protocol.record.ValueType#EVENT_BRIDGE_TOPIC}) and the broker registry (riding
 * {@link io.camunda.zeebe.protocol.record.ValueType#EVENT_BRIDGE_BROKER}). Each command/event maps
 * to its own processor/applier in the event-bridge cluster-metadata module.
 */
public enum MetadataIntent implements Intent {
  /**
   * Command: internal upsert of a topic's desired configuration (status flips, reconfiguration).
   */
  REGISTER_TOPIC((short) 0, false),
  /** Event: the topic has been registered in replicated state. */
  TOPIC_REGISTERED((short) 1, true),
  /** Command: remove a topic from the registry (client request, validated in the processor). */
  DELETE_TOPIC((short) 2, false),
  /** Event: the topic has been removed from replicated state. */
  TOPIC_DELETED((short) 3, true),
  /** Command: create a topic (client request; the processor rejects an existing name). */
  CREATE_TOPIC((short) 4, false),
  /**
   * Command: reassign a topic's replicas (client request; the processor rejects an unknown name).
   */
  REASSIGN_TOPIC((short) 5, false),
  /** Command: register (or re-register) a broker, assigning it a fresh broker epoch. */
  REGISTER_BROKER((short) 6, false),
  /** Event: the broker has been registered (or re-registered) in replicated state. */
  BROKER_REGISTERED((short) 7, true),
  /** Command: fence a broker whose liveness session lapsed (internal, from the eviction task). */
  FENCE_BROKER((short) 8, false),
  /** Event: the broker has been fenced — excluded from placement until it re-registers. */
  BROKER_FENCED((short) 9, true),
  /** Command: mark a broker as draining for controlled shutdown (internal, from a heartbeat). */
  DRAIN_BROKER((short) 10, false),
  /** Event: the broker is draining — excluded from placement while its replicas move off. */
  BROKER_DRAINING((short) 11, true),
  /** Command: deregister a broker (internal, once it has drained / shut down). */
  DEREGISTER_BROKER((short) 12, false),
  /** Event: the broker has been removed from replicated state. */
  BROKER_DEREGISTERED((short) 13, true);

  private final short value;
  private final boolean isEvent;

  MetadataIntent(final short value, final boolean isEvent) {
    this.value = value;
    this.isEvent = isEvent;
  }

  public static Intent from(final short value) {
    switch (value) {
      case 0:
        return REGISTER_TOPIC;
      case 1:
        return TOPIC_REGISTERED;
      case 2:
        return DELETE_TOPIC;
      case 3:
        return TOPIC_DELETED;
      case 4:
        return CREATE_TOPIC;
      case 5:
        return REASSIGN_TOPIC;
      case 6:
        return REGISTER_BROKER;
      case 7:
        return BROKER_REGISTERED;
      case 8:
        return FENCE_BROKER;
      case 9:
        return BROKER_FENCED;
      case 10:
        return DRAIN_BROKER;
      case 11:
        return BROKER_DRAINING;
      case 12:
        return DEREGISTER_BROKER;
      case 13:
        return BROKER_DEREGISTERED;
      default:
        return Intent.UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return isEvent;
  }
}
