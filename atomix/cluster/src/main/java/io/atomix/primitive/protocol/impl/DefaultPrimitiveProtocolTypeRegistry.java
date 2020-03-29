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
package io.atomix.primitive.protocol.impl;

import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.protocol.PrimitiveProtocolTypeRegistry;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Primitive protocol type registry. */
public class DefaultPrimitiveProtocolTypeRegistry implements PrimitiveProtocolTypeRegistry {
  private final Map<String, PrimitiveProtocol.Type> protocolTypes = new ConcurrentHashMap<>();

  public DefaultPrimitiveProtocolTypeRegistry(
      final Collection<PrimitiveProtocol.Type> protocolTypes) {
    protocolTypes.forEach(type -> this.protocolTypes.put(type.name(), type));
  }

  @Override
  public Collection<PrimitiveProtocol.Type> getProtocolTypes() {
    return protocolTypes.values();
  }

  @Override
  public PrimitiveProtocol.Type getProtocolType(final String type) {
    return protocolTypes.get(type);
  }
}
