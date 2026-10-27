/*
 * Copyright 2019-present Open Networking Foundation
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
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

/** V2 messaging protocol. */
public class MessagingProtocolV2 implements MessagingProtocol {
  private final Address address;

  MessagingProtocolV2(final Address address) {
    this.address = address;
  }

  @Override
  public ProtocolVersion version() {
    return ProtocolVersion.V2;
  }

  @Override
  public MessageToByteEncoder<Object> newEncoder() {
    return new MessageEncoderV2(address);
  }

  @Override
  public ByteToMessageDecoder newDecoder() {
    return new MessageDecoderV2();
  }
}
