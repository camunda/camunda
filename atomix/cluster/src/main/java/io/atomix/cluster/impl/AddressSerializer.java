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
package io.atomix.cluster.impl;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import io.atomix.utils.net.Address;

/** Address serializer. */
public class AddressSerializer extends com.esotericsoftware.kryo.Serializer<Address> {
  @Override
  public void write(final Kryo kryo, final Output output, final Address address) {
    output.writeString(address.host());
    output.writeInt(address.port());
  }

  @Override
  public Address read(final Kryo kryo, final Input input, final Class<Address> type) {
    final String host = input.readString();
    final int port = input.readInt();
    return Address.from(host, port);
  }
}
