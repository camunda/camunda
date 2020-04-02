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

/** State machine replication compatible primitive. */
public interface ProxyCompatibleBuilder<B extends ProxyCompatibleBuilder<B>> {

  /**
   * Configures the builder with a state machine replication protocol.
   *
   * @param protocol the state machine replication protocol
   * @return the primitive builder
   */
  B withProtocol(ProxyProtocol protocol);
}
