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
package io.atomix.primitive.protocol;

import java.util.Collection;

/** Primitive protocol type registry. */
public interface PrimitiveProtocolTypeRegistry {

  /**
   * Returns the collection of registered protocol types.
   *
   * @return the collection of registered protocol types
   */
  Collection<PrimitiveProtocol.Type> getProtocolTypes();

  /**
   * Returns the protocol type for the given configuration.
   *
   * @param type the type name for which to return the protocol type
   * @return the protocol type for the given configuration
   */
  PrimitiveProtocol.Type getProtocolType(String type);
}
