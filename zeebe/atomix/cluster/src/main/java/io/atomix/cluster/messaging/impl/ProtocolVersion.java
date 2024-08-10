/*
 * Copyright 2018-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.cluster.messaging.impl;

import io.atomix.utils.net.Address;
import java.util.stream.Stream;

/** Messaging protocol version. */
public enum ProtocolVersion {
  V1(1) {
    @Override
    public MessagingProtocol createProtocol(final Address address) {
      return new MessagingProtocolV1(address);
    }
  },
  V2(2) {
    @Override
    public MessagingProtocol createProtocol(final Address address) {
      return new MessagingProtocolV2(address);
    }
  };

  private final short version;

  ProtocolVersion(final int version) {
    this.version = (short) version;
  }

  /**
   * Returns the protocol version for the given version number.
   *
   * @param version the version number for which to return the protocol version
   * @return the protocol version for the given version number
   */
  public static ProtocolVersion valueOf(final int version) {
    return Stream.of(values()).filter(v -> v.version() == version).findFirst().orElse(null);
  }

  /**
   * Returns the latest protocol version.
   *
   * @return the latest protocol version
   */
  public static ProtocolVersion latest() {
    return values()[values().length - 1];
  }

  /**
   * Returns the version number.
   *
   * @return the version number
   */
  public short version() {
    return version;
  }

  /**
   * Creates a new protocol instance.
   *
   * @param address the protocol address
   * @return a new protocol instance
   */
  public abstract MessagingProtocol createProtocol(Address address);
}
