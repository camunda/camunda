/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.cluster.messaging.impl;

/** Base class for internal messages. */
public abstract class ProtocolMessage {

  private final long id;
  private final byte[] payload;

  protected ProtocolMessage(final long id, final byte[] payload) {
    this.id = id;
    this.payload = payload;
  }

  public abstract Type type();

  public boolean isRequest() {
    return type() == Type.REQUEST;
  }

  public boolean isReply() {
    return type() == Type.REPLY;
  }

  public long id() {
    return id;
  }

  public byte[] payload() {
    return payload;
  }

  /** Internal message type. */
  public enum Type {
    REQUEST(1),
    REPLY(2);

    private final int id;

    Type(final int id) {
      this.id = id;
    }

    /**
     * Returns the unique message type ID.
     *
     * @return the unique message type ID.
     */
    public int id() {
      return id;
    }

    /**
     * Returns the message type enum associated with the given ID.
     *
     * @param id the type ID.
     * @return the type enum for the given ID.
     */
    public static Type forId(final int id) {
      switch (id) {
        case 1:
          return REQUEST;
        case 2:
          return REPLY;
        default:
          throw new IllegalArgumentException("Unknown status ID " + id);
      }
    }
  }
}
